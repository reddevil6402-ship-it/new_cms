const fs = require('fs');

let html = fs.readFileSync('cms.html', 'utf8');

// 1. Fix corrupted characters in the HTML
html = html.replace(/\?"/g, '-"');
html = html.replace(/A/g, '-');
html = html.replace(/\?/g, '-');

// Make sure Diagram 2 is clean
const diag2Start = html.indexOf('<div class="section" id="diag-2">');
const mermaidStart = html.indexOf('<div class="mermaid">', diag2Start) + 21;
const mermaidEnd = html.indexOf('</div></div></div>', mermaidStart);

const diag2clean = `C4Container
    title NextGen CMS - Container Diagram (Database-per-Service)

    Person(user, "CMS User", "Author, Reviewer, Admin")
    Person(visitor, "Public Visitor")

    Container_Boundary(frontend, "Frontend") {
        Container(admin_app, "Admin Next.js App", "Next.js 14 + TypeScript", "CMS admin interface - dynamic forms, workflow UI, schema designer")
        Container(public_app, "Public Next.js App", "Next.js 14 + TypeScript", "Public-facing website with SSR/ISR content delivery")
    }

    Container_Boundary(gateway, "API Gateway") {
        Container(apigw, "API Gateway", "Kong / Nginx", "JWT validation, rate limiting, routing, SSL termination")
    }

    Container_Boundary(services, "Microservices") {
        Container(content_svc, "Content Service", "Spring Boot 3 / Java 21", "Universal content CRUD, lifecycle, versioning. Replaces all 28 module services.")
        Container(schema_svc, "Schema Service", "Spring Boot 3 / Java 21", "Dynamic content type registry, field definitions, categories, sites, menus")
        Container(workflow_svc, "Workflow Service", "Spring Boot 3 / Java 21", "Data-driven state machine engine for content approval workflows")
        Container(media_svc, "Media Service", "Spring Boot 3 / Java 21", "File upload, storage backend abstraction, thumbnail generation")
        Container(iam_svc, "IAM Service", "Spring Boot 3 / Java 21", "Tenants, users, roles, permissions, JWT issuance, RBAC enforcement")
        Container(form_svc, "Form Service", "Spring Boot 3 / Java 21", "Dynamic form schema management and submission handling")
        Container(search_svc, "Search Service", "Spring Boot 3 / Java 21", "Full-text search, faceted filtering, OpenSearch index management")
        Container(notif_svc, "Notification Service", "Spring Boot 3 / Java 21", "Email, SMS, in-app notification dispatch via templates")
        Container(audit_svc, "Audit Service", "Spring Boot 3 / Java 21", "Append-only immutable event log - pure Kafka consumer")
    }

    Container_Boundary(infra, "Infrastructure (Shared)") {
        Container(kafka, "Apache Kafka", "Message Broker", "Event streaming: content lifecycle, workflow transitions, media events")
        Container(redis, "Redis Cluster", "Cache", "Content cache, session store, schema cache, permission cache, menu cache")
    }

    Container_Boundary(dbs, "Databases (9 Separate Instances)") {
        ContainerDb(iamdb, "iamdb", "PostgreSQL 16", "tenants, users, roles, permissions, api_keys, refresh_tokens, outbox_events")
        ContainerDb(schemadb, "schemadb", "PostgreSQL 16", "content_types, field_definitions, categories, sites, menus, menu_items, outbox_events")
        ContainerDb(contentdb, "contentdb", "PostgreSQL 16", "content_items (JSONB body), content_versions, outbox_events")
        ContainerDb(workflowdb, "workflowdb", "PostgreSQL 16", "workflow_definitions, workflow_instances, workflow_history, outbox_events")
        ContainerDb(mediadb, "mediadb", "PostgreSQL 16", "media_assets, outbox_events")
        ContainerDb(formdb, "formdb", "PostgreSQL 16", "form_definitions, form_submissions, outbox_events")
        ContainerDb(opensearch, "searchstore", "OpenSearch 2.x", "Denormalized search documents - one index per tenant")
        ContainerDb(notifdb, "notificationdb", "PostgreSQL 16", "notification_templates, notification_log, outbox_events")
        ContainerDb(auditdb, "auditdb", "PostgreSQL 16 (partitioned)", "audit_events partitioned by month - append-only, no outbox")
    }

    Rel(user, admin_app, "Uses", "HTTPS")
    Rel(visitor, public_app, "Views", "HTTPS")
    Rel(admin_app, apigw, "API calls", "REST/HTTPS")
    Rel(public_app, apigw, "API calls", "REST/HTTPS")

    Rel(apigw, content_svc, "Routes to", "HTTP")
    Rel(apigw, schema_svc, "Routes to", "HTTP")
    Rel(apigw, workflow_svc, "Routes to", "HTTP")
    Rel(apigw, media_svc, "Routes to", "HTTP")
    Rel(apigw, iam_svc, "Routes to", "HTTP")
    Rel(apigw, search_svc, "Routes to", "HTTP")

    Rel(content_svc, contentdb, "Reads/Writes", "JDBC")
    Rel(schema_svc, schemadb, "Reads/Writes", "JDBC")
    Rel(workflow_svc, workflowdb, "Reads/Writes", "JDBC")
    Rel(media_svc, mediadb, "Reads/Writes", "JDBC")
    Rel(iam_svc, iamdb, "Reads/Writes", "JDBC")
    Rel(form_svc, formdb, "Reads/Writes", "JDBC")
    Rel(notif_svc, notifdb, "Reads/Writes", "JDBC")
    Rel(audit_svc, auditdb, "Writes", "JDBC")
    Rel(search_svc, opensearch, "Reads/Writes", "REST")

    Rel(content_svc, kafka, "Publishes", "Debezium CDC")
    Rel(schema_svc, kafka, "Publishes", "Debezium CDC")
    Rel(workflow_svc, kafka, "Publishes", "Debezium CDC")
    Rel(media_svc, kafka, "Publishes", "Debezium CDC")
    Rel(iam_svc, kafka, "Publishes", "Debezium CDC")
    Rel(form_svc, kafka, "Publishes", "Debezium CDC")

    Rel(search_svc, kafka, "Consumes", "Kafka Client")
    Rel(audit_svc, kafka, "Consumes", "Kafka Client")
    Rel(notif_svc, kafka, "Consumes", "Kafka Client")`;

if (diag2Start !== -1 && mermaidStart !== -1 && mermaidEnd !== -1) {
    html = html.substring(0, mermaidStart) + diag2clean + html.substring(mermaidEnd);
}

// 2. Inject svg-pan-zoom and rewrite mermaid initialization
const scriptOld = `<script type="module">
  import mermaid from 'https://cdn.jsdelivr.net/npm/mermaid@10/dist/mermaid.esm.min.mjs';
  mermaid.initialize({ startOnLoad: true, theme: 'dark', securityLevel: 'loose',
    themeVariables: { primaryColor: '#1e3a5f', primaryTextColor: '#e2e8f0', primaryBorderColor: '#3b82f6',
      lineColor: '#64748b', secondaryColor: '#0f1f35', tertiaryColor: '#162032',
      background: '#0a0f1a', mainBkg: '#1e3a5f', nodeBorder: '#3b82f6',
      clusterBkg: '#0f1f35', titleColor: '#93c5fd', edgeLabelBackground: '#1e293b',
      activeTaskBkgColor: '#1d4ed8', activeTaskBorderColor: '#3b82f6',
      fontFamily: 'JetBrains Mono, monospace'
    }
  });
  window.mermaid = mermaid;
</script>`;

const scriptNew = `<script src="https://cdn.jsdelivr.net/npm/svg-pan-zoom@3.6.1/dist/svg-pan-zoom.min.js"></script>
<script type="module">
  import mermaid from 'https://cdn.jsdelivr.net/npm/mermaid@10/dist/mermaid.esm.min.mjs';
  mermaid.initialize({ startOnLoad: false, theme: 'dark', securityLevel: 'loose',
    themeVariables: { primaryColor: '#1e3a5f', primaryTextColor: '#e2e8f0', primaryBorderColor: '#3b82f6',
      lineColor: '#64748b', secondaryColor: '#0f1f35', tertiaryColor: '#162032',
      background: '#0a0f1a', mainBkg: '#1e3a5f', nodeBorder: '#3b82f6',
      clusterBkg: '#0f1f35', titleColor: '#93c5fd', edgeLabelBackground: '#1e293b',
      activeTaskBkgColor: '#1d4ed8', activeTaskBorderColor: '#3b82f6',
      fontFamily: 'JetBrains Mono, monospace'
    }
  });
  window.mermaid = mermaid;
  
  document.addEventListener("DOMContentLoaded", async () => {
    const diagrams = document.querySelectorAll('.mermaid');
    for (let i = 0; i < diagrams.length; i++) {
      const el = diagrams[i];
      try {
        const source = el.textContent.trim();
        if (!source) continue;
        el.textContent = '';
        const id = 'mermaid-svg-' + i;
        const { svg } = await mermaid.render(id, source);
        el.innerHTML = svg;
        const svgElement = el.querySelector('svg');
        if (svgElement) {
          svgElement.style.maxWidth = '100%';
          svgElement.style.height = '600px';
          // Fix for some svg-pan-zoom sizing issues
          svgElement.setAttribute('width', '100%');
          svgElement.setAttribute('height', '100%');
          svgPanZoom(svgElement, { 
            zoomEnabled: true, 
            controlIconsEnabled: true, 
            fit: true, 
            center: true 
          });
        }
      } catch (err) {
        console.error('Mermaid render error', err);
        el.innerHTML = '<div style="color:red;padding:20px;border:1px solid red;background:#333;">Diagram rendering failed.</div><pre style="color:white;padding:20px;">'+err.message+'</pre>';
      }
    }
  });
</script>`;

html = html.replace(scriptOld, scriptNew);

fs.writeFileSync('cms.html', html, 'utf8');
console.log('Fixed cms.html');
