/**
 * The pack's list screen (medicines, cards, orders…): the signed-in person's rows, their status chips
 * and the actions each row offers. The agent sees the same list as screen context and runs the same
 * actions (see PackActions in App.tsx); a button here goes through the same rules.
 */
import { ScrollView, Text, View } from "react-native";
import { useDemo } from "../state/DemoState";
import { useScreenContext } from "../graine";
import { currentStatus, rowButtons, runPackAction } from "../logic/actions";
import { Body, Button, Card, Chip, styles, useColors } from "../ui/components";

export function ItemsScreen() {
  const { pack, rows, statuses, openRowId, setOpenRow, setRowStatus } = useDemo();
  const c = useColors();

  useScreenContext({
    screen: pack.items.screenName,
    title: pack.items.title,
    fields: rows.map((r) => ({ name: r.id, label: r.title, value: currentStatus(r, statuses) ?? r.subtitle, status: "filled" as const })),
  });

  return (
    <ScrollView style={{ backgroundColor: c.background }} contentContainerStyle={styles.screen}>
      {rows.length === 0 ? <Body muted>{pack.items.emptyText}</Body> : null}
      {rows.map((row) => {
        const open = openRowId === row.id;
        const status = currentStatus(row, statuses);
        return (
          <Card
            key={row.id}
            onPress={() => setOpenRow(open ? null : row.id)}
            accessibilityLabel={`${row.title}${status ? `, ${status}` : ""}`}
            style={open ? { borderColor: c.accent, borderWidth: 2 } : undefined}
          >
            <View style={styles.row}>
              <Text style={{ color: c.text, fontSize: 16, fontWeight: "600", flex: 1 }}>{row.title}</Text>
              {status ? <Chip label={status} tone="neutral" /> : null}
            </View>
            <Body muted>{row.subtitle}</Body>
            {open ? (
              <View style={{ marginTop: 10, gap: 6 }}>
                {row.fields.map((f) => (
                  <View key={f.name} style={styles.row}>
                    <Text style={{ color: c.textMuted, width: 110, fontSize: 14 }}>{f.label}</Text>
                    <Text style={{ color: c.text, flex: 1, fontSize: 14 }}>{f.value}</Text>
                  </View>
                ))}
                <View style={[styles.wrap, { marginTop: 6 }]}>
                  {rowButtons(pack, row, statuses).map((action) => (
                    <Button
                      key={action.name}
                      label={action.label}
                      kind="secondary"
                      onPress={() => {
                        const { effect } = runPackAction(pack, action, { item: row.id }, rows, statuses);
                        if (effect?.type === "set_status") setRowStatus(effect.rowId, effect.status);
                      }}
                    />
                  ))}
                </View>
              </View>
            ) : null}
          </Card>
        );
      })}
    </ScrollView>
  );
}
