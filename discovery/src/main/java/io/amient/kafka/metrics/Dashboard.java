package io.amient.kafka.metrics;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.FileOutputStream;
import java.io.IOException;

public class Dashboard {
    private final ObjectMapper mapper = new ObjectMapper();
    private final ObjectNode root;
    private final ArrayNode rows;
    private final String filename;
    private final String dataSource;

    public Dashboard(String title, String dataSource, String filename) {
        this.dataSource = dataSource;
        this.filename = filename;
        root = mapper.createObjectNode();
        root.put("schemaVersion", 7);
        root.put("id", (String) null);
        root.put("version", 0);
        root.put("title", title);
        root.put("originalTitle", title);
        root.put("style", "dark");
        root.put("timezone", "browser");
        root.put("refresh", "10s");
        root.set("time", mapper.createObjectNode().put("from", "now-30m").put("to", "now"));
        root.put("editable", true);
        root.put("hideControls", false);
        root.put("sharedCrosshair", false);
        root.set("links", mapper.createArrayNode());
        root.set("tags", mapper.createArrayNode());
        root.set("templating", mapper.createObjectNode().set("list", mapper.createArrayNode()));
        root.set("annotations", mapper.createObjectNode().set("list", mapper.createArrayNode()));
        rows = mapper.createArrayNode();
        root.set("rows", rows);
    }

    public void save() {
        mapper.enable(SerializationFeature.INDENT_OUTPUT);
        try {
            FileOutputStream out = new FileOutputStream(filename);
            try {
                mapper.writeValue(out, root);
            } finally {
                out.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public ArrayNode newRow(String rowTitle, int heightPx) {
        ObjectNode row = rows.addObject();
        row.put("title", rowTitle);
        row.put("showTitle", rowTitle != null);
        row.put("height", heightPx + "px");
        row.put("editable", true);
        row.put("collapse", false);
        ArrayNode panels = mapper.createArrayNode();
        row.set("panels", panels);
        return panels;
    }

    public ObjectNode newGraph(ArrayNode rowPanels, String title, int span, boolean showLegend) {
        ObjectNode graph = newPanel(rowPanels, title, span, "graph");
        //
        graph.put("nullPointMode", "connected");
        graph.put("steppedLine", false);
        graph.put("x-axis", true);
        graph.put("y-axis", true);
        graph.put("lines", true);
        graph.put("fill", 1);
        graph.put("linewidth", 2);
        graph.put("points", false);
        graph.put("pointradius", 5);
        graph.put("bars", false);
        graph.put("percentage", false);
        graph.set("y_formats", mapper.createArrayNode()
                .add("short")
                .add("short"));
        //
        graph.put("stack", false);
        graph.set("tooltip", mapper.createObjectNode()
            .put("value_type", "cumulative")
            .put("shared", true));
        //
        graph.set("seriesOverrides", mapper.createArrayNode());
        graph.set("aliasColors", mapper.createObjectNode());
        graph.set("legend", mapper.createObjectNode()
            .put("show", showLegend)
            .put("values", false)
            .put("min", false)
            .put("max", false)
            .put("current", false)
            .put("total", false)
            .put("avg", false));
        //
        graph.set("grid", mapper.createObjectNode()
            .put("leftLogBase", 1)
            .put("leftMax", (Integer)null)
            .put("rightMax", (Integer)null)
            .put("leftMin", (Integer)null)
            .put("rightMin", (Integer)null)
            .put("rightLogBase", (Integer)1)
            .put("threshold1", (Integer)null)
            .put("threshold2", (Integer)null)
            .put("threshold1Color", "rgba(216, 200, 27, 0.27)")
            .put("threshold2Color", "rgba(234, 112, 112, 0.22)"));

        graph.set("targets", mapper.createArrayNode());
        return graph;
    }

    public ObjectNode newTarget(ObjectNode panel, String aliasPattern, String rawQuery) {
        ObjectNode target = ((ArrayNode) panel.get("targets")).addObject();
        target.put("query", rawQuery);
        target.put("alias", aliasPattern);
        target.put("rawQuery", true);
        return target;
    }

    public ObjectNode newTarget(ObjectNode panel) {
        ObjectNode target = ((ArrayNode) panel.get("targets")).addObject();
        target.put("rawQuery", false);

        target.put("measurement", "UnderReplicatedPartitions");

        ArrayNode fields = mapper.createArrayNode();
        fields.addObject().put("name", "Value").put("func", "mean");
        target.set("fields", fields);

        ArrayNode groupBy = mapper.createArrayNode();
        groupBy.addObject().put("type", "time").put("interval", "auto");
        target.set("groupBy", groupBy);

//        ArrayNode tags = mapper.createArrayNode();
//        tags.addObject().put(...).put(...).put(...);
//        target.set("tags", tags);

        return target;
    }

    private ObjectNode newPanel(ArrayNode rowPanels, String title, int span, String type) {
        ObjectNode panel = rowPanels.addObject();
        panel.put("title", title);
        panel.put("span", span);
        panel.put("id", rowPanels.size());
        panel.put("datasource", dataSource);
        panel.put("type", type);
        panel.put("renderer", "flot");
        //
        panel.put("timeFrom", (String) null);
        panel.put("timeShift", (String) null);

        //
        panel.put("editable", true);
        panel.put("error", false);
        panel.put("isNew", true);
        return panel;
    }

}
