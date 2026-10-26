/**
 * Shared by check-usecases.mjs and build-agent-setup.mjs: a pack's actions in the format the
 * dashboard's Embed & Widgets → App actions → Import from JSON accepts (a bare array of
 * { tool_name, name, kind, description, parameters }), so the agent declares exactly what the apps
 * register — names, arguments and enums included.
 */

const KIND = { open_screen: "navigation", open_item: "navigation", set_status: "action" };

export function actionsFor(pack) {
  return pack.actions.map((a) => {
    const properties = {};
    const required = [];
    for (const arg of a.args) {
      properties[arg.name] = {
        type: arg.type,
        description: arg.description,
        ...(arg.enum ? { enum: arg.enum } : {}),
      };
      if (arg.required) required.push(arg.name);
    }
    return {
      tool_name: a.name,
      name: a.name,
      kind: KIND[a.effect] ?? "action",
      description: a.description,
      parameters: { type: "object", properties, required },
    };
  });
}

/** The file's exact text: two-space JSON and a final newline. */
export function actionsJsonFor(pack) {
  return `${JSON.stringify(actionsFor(pack), null, 2)}\n`;
}

/**
 * The journey's first-call flow as the dashboard imports it (Import JSON, Settings → Advanced → Raw flow,
 * the agents API's `flow`): the pack's own, unchanged. Null for a pack without one.
 */
export function flowJsonFor(pack) {
  const flow = pack.journey?.firstCall?.flow;
  return flow ? `${JSON.stringify(flow, null, 2)}\n` : null;
}
