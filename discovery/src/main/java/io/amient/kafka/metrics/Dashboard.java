/*
 * Copyright 2015 Michal Harish, michal.harish@gmail.com
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.amient.kafka.metrics;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import joptsimple.internal.Strings;

import java.io.FileOutputStream;
import java.io.IOException;

public class Dashboard {
    private final ObjectMapper mapper = new ObjectMapper();
    private final ObjectNode root;
    private final ArrayNode rows;
    private final String filename;
    private final String dataSource;
    private final ArrayNode templating;
    private int numPanels = 0;

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
        templating = mapper.createArrayNode();
        root.set("templating", mapper.createObjectNode().set("list", templating));
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

    public ArrayNode newRow(String rowTitle, int heightPx, boolean expand) {
        ObjectNode row = rows.addObject();
        row.put("title", rowTitle);
        row.put("showTitle", rowTitle != null);
        row.put("height", heightPx + "px");
        row.put("editable", true);
        row.put("collapse", !expand);
        ArrayNode panels = mapper.createArrayNode();
        row.set("panels", panels);
        return panels;
    }

    public ObjectNode newGraph(ArrayNode rowPanels, String title, int span, boolean showLegend) {
        ObjectNode graph = newPanel(rowPanels, title, span, "graph");
        //
        graph.put("nullPointMode", "connected");
        graph.put("x-axis", true);
        graph.put("y-axis", true);
        graph.set("y_formats", mapper.createArrayNode().add("short").add("short"));
        graph.put("lines", true);
        graph.put("linewidth", 2);
        graph.put("steppedLine", false);
        graph.put("fill", 1);
        graph.put("points", false);
        graph.put("pointradius", 2);
        graph.put("bars", false);
        graph.put("percentage", false);
        graph.put("stack", false);
        //
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
                .put("leftMax", (Integer) null)
                .put("rightMax", (Integer) null)
                .put("leftMin", (Integer) null)
                .put("rightMin", (Integer) null)
                .put("rightLogBase", (Integer) 1)
                .put("threshold1", (Integer) null)
                .put("threshold1Color", "rgba(216, 200, 27, 0.27)")
                .put("threshold2", (Integer) null)
                .put("threshold2Color", "rgba(234, 112, 112, 0.22)"));

        return graph;
    }

    public ObjectNode newTable(ArrayNode rowPanels, String title, int span, String valueName, String alias, String query) {
        ObjectNode table = newPanel(rowPanels, title, span, "table");
        table.put("transform", "timeseries_aggregations");
        newTarget(table, alias, query);
        //
        ArrayNode columns = mapper.createArrayNode();
        columns.addObject().put("value", valueName).put("text", valueName);
        table.set("columns", columns);
        ArrayNode styles = mapper.createArrayNode();
        styles.addObject()
                .put("value", valueName)
                .put("type", "number")
                .put("pattern", "/.*/")
                .put("decimals", 0)
                //.put("colorMode", null)//
                .put("unit", "short");
        table.set("styles", styles);
        //
        table.put("showHeader", true);
        table.put("scroll", true);
        table.put("fontSize", "100%");
        table.put("pageSize", (Integer) null);
        table.set("sort", mapper.createObjectNode().put("col", (String) null).put("desc", false));
        return table;
    }

    public ObjectNode newStat(ArrayNode rowPanels, String title, int span, String query) {
        ObjectNode stat = newPanel(rowPanels, title, span, "singlestat");
        stat.put("valueName", "current");
        stat.put("decimals", 0);
        stat.put("maxDataPoints", 100);
        stat.put("prefix", "");
        stat.put("postfix", "");
        stat.put("nullText", (String) null);
        stat.put("prefixFontSize", "50%");
        stat.put("valueFontSize", "80%");
        stat.put("postfixFontSize", "50%");
        stat.put("format", "none");
        stat.put("nullPointMode", "connected");
        stat.set("sparkline", mapper.createObjectNode()
                .put("show", false)
                .put("full", false)
        );
//        "thresholds": "",
//        "colorBackground": false,
//        "colorValue": false,
        newTarget(stat, "", query);
        return stat;
    }

    public ObjectNode newVariable(String name, boolean includeAll, String... options) {
        ObjectNode variable = templating.addObject()
                .put("type", "custom")
                .put("name", name)
                .put("label", name)
                .put("includeAll", includeAll)
                .put("multi", false)
                .put("query", Strings.join(options, ","))
                .put("datasource", (String) null)
                .put("refresh", 0)
                .put("hide", 0);
        variable.set("current", mapper.createObjectNode()
                .put("text", "All")
                .put("value", "$__all")
                .set("tags", mapper.createArrayNode()));
        ArrayNode optionsArray = mapper.createArrayNode();
        variable.set("options", optionsArray);
        if (includeAll) {
            variable.put("allValue", ".+");
            optionsArray.addObject().put("text", "All").put("value", "$__all").put("selected", true);
        }
        for(String option: options) {
            optionsArray.addObject().put("text", option).put("value", option).put("selected", false);
        }
        return variable;
    }

    public ObjectNode newTarget(ObjectNode panel, String aliasPattern, String rawQuery) {
        ArrayNode targets = ((ArrayNode) panel.get("targets"));
        ObjectNode target = targets.addObject();
        target.put("refId", Character.toString((char) (64 + targets.size())));
        target.put("query", rawQuery);
        target.put("alias", aliasPattern);
        target.put("rawQuery", true);
        return target;
    }

//    public ObjectNode newTarget(ObjectNode panel) {
//        ObjectNode target = ((ArrayNode) panel.get("targets")).addObject();
//        target.put("rawQuery", false);
//
//        target.put("measurement", "UnderReplicatedPartitions");
//
//        ArrayNode fields = mapper.createArrayNode();
//        fields.addObject().put("name", "Value").put("func", "mean");
//        target.set("fields", fields);
//
//        ArrayNode groupBy = mapper.createArrayNode();
//        groupBy.addObject().put("type", "time").put("interval", "auto");
//        target.set("groupBy", groupBy);
//
//        ArrayNode tags = mapper.createArrayNode();
//        tags.addObject().put(...).put(...).put(...);
//        target.set("tags", tags);
//
//        return target;
//    }

    private ObjectNode newPanel(ArrayNode rowPanels, String title, int span, String type) {
        ObjectNode panel = rowPanels.addObject();
        panel.put("title", title);
        panel.put("span", span);
        panel.put("id", ++numPanels);
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
        //
        panel.set("targets", mapper.createArrayNode());
        return panel;
    }


    public ObjectNode newObject() {
        return mapper.createObjectNode();
    }

    public ArrayNode newArray(String... values) {
        ArrayNode node = mapper.createArrayNode();
        for (String v : values) node.add(v);
        return node;
    }

    public ObjectNode get(ObjectNode node, String fieldName) {
        return (ObjectNode) node.get(fieldName);
    }
}
