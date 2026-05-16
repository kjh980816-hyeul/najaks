package com.najacks.backend.notion;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.time.LocalDate;
import java.util.List;

/**
 * Notion 속성 JSON 빌더 헬퍼. 페이지 생성·수정 시 properties 필드에 넣을 JSON 객체 생성.
 */
public final class NotionProperties {

    private static final JsonNodeFactory F = JsonNodeFactory.instance;

    private NotionProperties() {}

    public static ObjectNode title(String text) {
        ObjectNode prop = F.objectNode();
        ArrayNode titleArr = prop.putArray("title");
        ObjectNode rt = titleArr.addObject();
        rt.put("type", "text");
        rt.putObject("text").put("content", truncate(text, 2000));
        return prop;
    }

    public static ObjectNode richText(String text) {
        ObjectNode prop = F.objectNode();
        ArrayNode rich = prop.putArray("rich_text");
        if (text != null && !text.isEmpty()) {
            ObjectNode rt = rich.addObject();
            rt.put("type", "text");
            rt.putObject("text").put("content", truncate(text, 2000));
        }
        return prop;
    }

    public static ObjectNode number(Number value) {
        ObjectNode prop = F.objectNode();
        if (value == null) prop.putNull("number");
        else prop.put("number", value.doubleValue());
        return prop;
    }

    public static ObjectNode select(String name) {
        ObjectNode prop = F.objectNode();
        if (name == null || name.isBlank()) {
            prop.putNull("select");
        } else {
            prop.putObject("select").put("name", name);
        }
        return prop;
    }

    public static ObjectNode status(String name) {
        ObjectNode prop = F.objectNode();
        if (name == null || name.isBlank()) {
            prop.putNull("status");
        } else {
            prop.putObject("status").put("name", name);
        }
        return prop;
    }

    public static ObjectNode multiSelect(List<String> names) {
        ObjectNode prop = F.objectNode();
        ArrayNode arr = prop.putArray("multi_select");
        if (names != null) {
            for (String n : names) {
                if (n == null || n.isBlank()) continue;
                arr.addObject().put("name", truncate(n, 100));
            }
        }
        return prop;
    }

    public static ObjectNode checkbox(boolean value) {
        ObjectNode prop = F.objectNode();
        prop.put("checkbox", value);
        return prop;
    }

    public static ObjectNode date(LocalDate value) {
        ObjectNode prop = F.objectNode();
        if (value == null) prop.putNull("date");
        else prop.putObject("date").put("start", value.toString());
        return prop;
    }

    public static ObjectNode dateRange(LocalDate start, LocalDate end) {
        ObjectNode prop = F.objectNode();
        if (start == null) { prop.putNull("date"); return prop; }
        ObjectNode d = prop.putObject("date");
        d.put("start", start.toString());
        if (end != null) d.put("end", end.toString());
        return prop;
    }

    public static ObjectNode url(String url) {
        ObjectNode prop = F.objectNode();
        if (url == null || url.isBlank()) prop.putNull("url");
        else prop.put("url", url);
        return prop;
    }

    public static ObjectNode heading2(String text) {
        ObjectNode block = F.objectNode();
        block.put("object", "block");
        block.put("type", "heading_2");
        ObjectNode h = block.putObject("heading_2");
        ArrayNode rt = h.putArray("rich_text");
        rt.addObject().put("type", "text").putObject("text").put("content", truncate(text, 2000));
        return block;
    }

    public static ObjectNode paragraph(String text) {
        ObjectNode block = F.objectNode();
        block.put("object", "block");
        block.put("type", "paragraph");
        ObjectNode p = block.putObject("paragraph");
        ArrayNode rt = p.putArray("rich_text");
        if (text != null && !text.isEmpty()) {
            rt.addObject().put("type", "text").putObject("text").put("content", truncate(text, 2000));
        }
        return block;
    }

    public static ObjectNode bulletedListItem(String text) {
        ObjectNode block = F.objectNode();
        block.put("object", "block");
        block.put("type", "bulleted_list_item");
        ObjectNode p = block.putObject("bulleted_list_item");
        ArrayNode rt = p.putArray("rich_text");
        rt.addObject().put("type", "text").putObject("text").put("content", truncate(text, 2000));
        return block;
    }

    public static ObjectNode callout(String emoji, String text) {
        ObjectNode block = F.objectNode();
        block.put("object", "block");
        block.put("type", "callout");
        ObjectNode c = block.putObject("callout");
        ArrayNode rt = c.putArray("rich_text");
        rt.addObject().put("type", "text").putObject("text").put("content", truncate(text, 2000));
        ObjectNode icon = c.putObject("icon");
        icon.put("type", "emoji");
        icon.put("emoji", emoji == null ? "💡" : emoji);
        return block;
    }

    private static String truncate(String s, int limit) {
        if (s == null) return "";
        return s.length() <= limit ? s : s.substring(0, limit);
    }
}
