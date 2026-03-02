package org.task.taskmaganer.aspect;

import org.springframework.stereotype.Component;

@Component
public class AuditMessageBuilder {

    public String build(AuditContext context) {
        String customMessage = context.getAuditLog().message();

        if (context.isSuccess()) {
            return buildSuccessMessage(context, customMessage);
        } else {
            return buildErrorMessage(context, customMessage);
        }
    }

    private String buildSuccessMessage(AuditContext context, String customMessage) {
        if (!customMessage.isEmpty()) {
            return customMessage;
        }

        StringBuilder sb = new StringBuilder();
        sb.append(context.getAction()).append(" operation successful");
        sb.append(" for ").append(context.getEntityType());

        appendEntityId(sb, context.getEntityId());
        appendExecutionTime(sb, context.getExecutionTime());
        appendParams(sb, context.getMethodParams());

        return sb.toString();
    }

    private String buildErrorMessage(AuditContext context, String customMessage) {
        String base = customMessage.isEmpty()
                ? buildErrorBase(context)
                : customMessage;

        return base + " | Error: " + context.getErrorMessage();
    }

    private String buildErrorBase(AuditContext context) {
        StringBuilder sb = new StringBuilder();
        sb.append(context.getAction()).append(" operation failed");
        sb.append(" for ").append(context.getEntityType());

        appendEntityId(sb, context.getEntityId());
        appendExecutionTime(sb, context.getExecutionTime(), "after");

        return sb.toString();
    }

    private void appendEntityId(StringBuilder sb, String entityId) {
        if (entityId != null) {
            sb.append(" (ID: ").append(entityId).append(")");
        }
    }

    private void appendExecutionTime(StringBuilder sb, long time) {
        appendExecutionTime(sb, time, "in");
    }

    private void appendExecutionTime(StringBuilder sb, long time, String preposition) {
        sb.append(" ").append(preposition).append(" ").append(time).append("ms");
    }

    private void appendParams(StringBuilder sb, java.util.Map<String, Object> params) {
        if (!params.isEmpty()) {
            sb.append(" | Params: ").append(params.keySet());
        }
    }
}
