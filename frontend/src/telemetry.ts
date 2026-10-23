import 'zone.js'
import { ZoneContextManager } from '@opentelemetry/context-zone'
import { OTLPTraceExporter } from '@opentelemetry/exporter-trace-otlp-http'
import { registerInstrumentations } from '@opentelemetry/instrumentation'
import { DocumentLoadInstrumentation } from '@opentelemetry/instrumentation-document-load'
import { FetchInstrumentation } from '@opentelemetry/instrumentation-fetch'
import { XMLHttpRequestInstrumentation } from '@opentelemetry/instrumentation-xml-http-request'
import { Resource } from '@opentelemetry/resources'
import { BatchSpanProcessor } from '@opentelemetry/sdk-trace-base'
import { WebTracerProvider } from '@opentelemetry/sdk-trace-web'
import { trace } from '@opentelemetry/api'

/**
 * Browser tracing. Spans go to the same-origin nginx route /otlp/v1/traces -> OpenTelemetry Collector -> Tempo.
 * fetch/XHR instrumentation injects the W3C `traceparent` header into every /api call, so the browser span
 * and the gateway/service spans end up in ONE trace.
 * Everything is wrapped: an unreachable collector or any SDK failure must never affect the app.
 */
export function initTelemetry(): void {
  try {
    const provider = new WebTracerProvider({
      resource: new Resource({
        'service.name': 'frontend',
        'service.namespace': 'linkedin',
        'deployment.environment': 'local',
      }),
      spanProcessors: [
        new BatchSpanProcessor(new OTLPTraceExporter({ url: '/otlp/v1/traces' }), {
          scheduledDelayMillis: 3000,
          maxExportBatchSize: 50,
        }),
      ],
    })
    provider.register({ contextManager: new ZoneContextManager() })

    const sameOrigin = [new RegExp('^' + window.location.origin.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')), /^\/api\//]
    const ignore = [/\/otlp\//]
    registerInstrumentations({
      instrumentations: [
        new DocumentLoadInstrumentation(),
        new FetchInstrumentation({ propagateTraceHeaderCorsUrls: sameOrigin, ignoreUrls: ignore, clearTimingResources: true }),
        new XMLHttpRequestInstrumentation({ propagateTraceHeaderCorsUrls: sameOrigin, ignoreUrls: ignore }),
      ],
    })

    const tracer = trace.getTracer('frontend')
    const report = (name: string, err: unknown) => {
      try {
        const span = tracer.startSpan(name)
        span.recordException(err instanceof Error ? err : String(err))
        span.setStatus({ code: 2, message: String(err) })
        span.end()
      } catch {
        /* ignore */
      }
    }
    window.addEventListener('error', (e) => report('window.error', e.error ?? e.message))
    window.addEventListener('unhandledrejection', (e) => report('window.unhandledrejection', e.reason))
  } catch (e) {
    console.warn('telemetry disabled', e)
  }
}
