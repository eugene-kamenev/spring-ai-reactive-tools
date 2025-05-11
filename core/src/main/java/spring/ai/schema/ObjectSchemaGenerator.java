package spring.ai.schema;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.victools.jsonschema.generator.*;
import com.github.victools.jsonschema.module.jackson.JacksonModule;
import com.github.victools.jsonschema.module.jackson.JacksonOption;

import java.lang.reflect.Type;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * Generates relaxed schema for POJO objects. Can be used to generate BAML or YAML schema.
 */
public class ObjectSchemaGenerator {

    private static final String INDENT = "  ";
    private static final String DEFINITIONS_PATH = "#/definitions/";

    private static final String DEFINITIONS_KEY = "definitions";
    private static final String PROPERTIES_KEY = "properties";
    private static final String TYPE_KEY = "type";
    private static final String REF_KEY = "$ref";
    private static final String ALL_OF_KEY = "allOf";
    private static final String DESCRIPTION_KEY = "description";
    private static final String ITEMS_KEY = "items";
    private static final String ENUM_KEY = "enum";

    private static final String ARRAY_TYPE = "array";
    private static final String OBJECT_TYPE = "object";
    private static final String STRING_TYPE = "string";

    public static String generateSimpleJsonSchema(Type entityClass) {
        return generateSchemaInternal(entityClass, false);
    }

    public static String generateYamlSchema(Type entityClass) {
        return generateSchemaInternal(entityClass, true);
    }

    public static String generateYamlSchema(JsonNode schemaNode) {
        var definitions = createDefinitionsMap(schemaNode);
        return generateYaml(schemaNode, 0, definitions);
    }

    public static String generateSimpleJsonSchema(JsonNode schemaNode) {
        var definitions = createDefinitionsMap(schemaNode);
        return generateJson(schemaNode, 1, true, definitions);
    }

    public static String generateRfcJsonSchema(Type entityClass, ObjectMapper mapper) {
        try {
            return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(generateRfcJsonSchema(entityClass));
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to generate JSON schema", e);
        }
    }

    private static String generateSchemaInternal(Type entityClass, boolean yaml) {
        var schemaNode = generateRfcJsonSchema(entityClass);
        var definitions = createDefinitionsMap(schemaNode);
        return yaml ? generateYaml(schemaNode, 0, definitions) : generateJson(schemaNode, 1, true, definitions);
    }

    private static JsonNode generateRfcJsonSchema(Type entityClass) {
        var jacksonModule = new JacksonModule(
            JacksonOption.RESPECT_JSONPROPERTY_REQUIRED,
            JacksonOption.RESPECT_JSONPROPERTY_ORDER
        );
        var generator = new SchemaGenerator(
            new SchemaGeneratorConfigBuilder(SchemaVersion.DRAFT_7, OptionPreset.PLAIN_JSON)
                .with(jacksonModule)
                .with(Option.FORBIDDEN_ADDITIONAL_PROPERTIES_BY_DEFAULT)
                .build()
        );
        return generator.generateSchema(entityClass);
    }

    private static Map<String, JsonNode> createDefinitionsMap(JsonNode schemaNode) {
        return schemaNode.has(DEFINITIONS_KEY) ?
                streamFields(schemaNode.get(DEFINITIONS_KEY))
                        .collect(Collectors.toMap(e -> DEFINITIONS_PATH + e.getKey(), Map.Entry::getValue)) :
                Collections.emptyMap();
    }

    private static String generateJson(JsonNode node, int indentLevel, boolean asObject, Map<String, JsonNode> definitions) {
        var result = new StringBuilder();
        if (indentLevel == 1) {
            if (node.has(DESCRIPTION_KEY)) {
                result.append(node.get(DESCRIPTION_KEY).asText()).append("\n");
            }
            result.append("```json\n");
        }

        if (asObject) {
            result.append(INDENT.repeat(indentLevel - 1)).append("{\n");
        }

        boolean firstField = true;
        for (Map.Entry<String, JsonNode> field : streamFields(node.get(PROPERTIES_KEY)).toList()) {
            if (!firstField) result.append(",\n");
            firstField = false;

            JsonNode fieldNode = resolveFieldNode(field.getValue(), definitions);
            appendJsonField(result, field.getKey(), fieldNode, indentLevel, definitions);
        }

        if (asObject) {
            result.append('\n').append(INDENT.repeat(indentLevel - 1)).append('}');
        }

        if (indentLevel == 1) {
            result.append("\n```");
        }

        return result.toString();
    }

    private static void appendJsonField(StringBuilder result, String key, JsonNode node, int indentLevel, Map<String, JsonNode> definitions) {
        var indent = INDENT.repeat(indentLevel);
        var value = node.has(DESCRIPTION_KEY) ? node.get(DESCRIPTION_KEY).asText() : "";
        var description = !value.isBlank() ?
                indent + "// " + node.get(DESCRIPTION_KEY).asText() + "\n" : "";

        result.append(description).append(indent).append(key).append(": ");

        switch (node.get(TYPE_KEY).asText()) {
            case ARRAY_TYPE:
                appendJsonArrayField(result, node.get(ITEMS_KEY), indentLevel, definitions);
                break;
            case OBJECT_TYPE:
                if (node.has(PROPERTIES_KEY)) {
                    appendJsonObjectField(result, node, indentLevel, definitions);
                } else {
                    result.append(node.get(TYPE_KEY).asText());
                }
                break;
            case STRING_TYPE:
                appendJsonStringField(result, node);
                break;
            default:
                result.append(node.get(TYPE_KEY).asText());
        }
    }

    private static void appendJsonArrayField(StringBuilder result, JsonNode itemsNode, int indentLevel, Map<String, JsonNode> definitions) {
        if (itemsNode.has(PROPERTIES_KEY)) {
            String nested = generateJson(itemsNode, indentLevel + 2, true, definitions);
            result.append("[\n").append(nested).append("\n").append(INDENT.repeat(indentLevel)).append("]");
        } else {
            result.append("[]").append(itemsNode.get(TYPE_KEY).asText());
        }
    }

    private static void appendJsonObjectField(StringBuilder result, JsonNode node, int indentLevel, Map<String, JsonNode> definitions) {
        String nested = generateJson(node, indentLevel + 1, false, definitions);
        result.append("{\n").append(nested).append("\n").append(INDENT.repeat(indentLevel)).append("}");
    }

    private static void appendJsonStringField(StringBuilder result, JsonNode node) {
        if (node.has(ENUM_KEY)) {
            result.append(enumSelection(streamElements(node.get(ENUM_KEY))));
        } else {
            result.append(STRING_TYPE);
        }
    }

    private static String enumSelection(Stream<JsonNode> nodes) {
        return nodes.map(JsonNode::asText)
                .map(e -> "\"" + e + "\"")
                .collect(Collectors.joining(" | "));
    }

    private static String generateYaml(JsonNode node, int indentLevel, Map<String, JsonNode> definitions) {
        var result = new StringBuilder();

        if (indentLevel == 0) {
            if (node.has(DESCRIPTION_KEY)) {
                result.append(node.get(DESCRIPTION_KEY).asText()).append("\n");
            }
            result.append("```yaml\n");
        }

        boolean firstField = true;
        for (Map.Entry<String, JsonNode> field : streamFields(node.get(PROPERTIES_KEY)).toList()) {
            if (!firstField) result.append("\n");
            firstField = false;

            var fieldNode = resolveFieldNode(field.getValue(), definitions);
            appendYamlField(result, field.getKey(), fieldNode, indentLevel, definitions);
        }

        if (indentLevel == 0) {
            result.append("\n```\n");
        }

        return result.toString();
    }

    private static void appendYamlField(StringBuilder result, String key, JsonNode node, int indentLevel, Map<String, JsonNode> definitions) {
        var indent = INDENT.repeat(indentLevel);
        result.append(indent).append(key).append(": ");

        // Add description as a YAML comment on the same line
        var description = "";
        if (node.has(DESCRIPTION_KEY)) {
            description = node.get(DESCRIPTION_KEY).asText();
        }

        var type = node.get(TYPE_KEY).asText();
        switch (type) {
            case ARRAY_TYPE:
                appendYamlArrayField(result, node.get(ITEMS_KEY), indentLevel, definitions);
                appendYamlComment(result, description);
                break;
            case OBJECT_TYPE:
                if (node.has(PROPERTIES_KEY)) {
                    appendYamlComment(result, description);
                    result.append("\n");
                    String nested = generateYaml(node, indentLevel + 1, definitions);
                    result.append(nested);
                } else {
                    result.append("object");
                    appendYamlComment(result, description);
                }
                break;
            case STRING_TYPE:
                appendYamlStringField(result, node);
                appendYamlComment(result, description);
                break;
            default:
                result.append(type);
                appendYamlComment(result, description);
        }
    }

    private static void appendYamlComment(StringBuilder result, String comment) {
        if (!comment.isBlank()) {
            result.append(" # ").append(comment);
        }
    }

    private static void appendYamlArrayField(StringBuilder result, JsonNode itemsNode, int indentLevel, Map<String, JsonNode> definitions) {
        if (itemsNode.has(PROPERTIES_KEY)) {
/*            result.append("[\n").append(INDENT.repeat(indentLevel + 1)).append("- {\n");
            var nested = generateYaml(itemsNode, indentLevel + 1, definitions);
            var indentedNested = nested.replaceAll("\n", ",\n" + INDENT.repeat(indentLevel + 1));
            result.append(INDENT).append(indentedNested);
            result.append("\n").append(INDENT.repeat(indentLevel + 1)).append("}\n").append(INDENT.repeat(indentLevel)).append("]\n");*/
            result.append("\n").append(INDENT.repeat(indentLevel + 1)).append("- ");
            String nested = generateYaml(itemsNode, indentLevel + 1, definitions);
            String indentedNested = nested.replaceAll("\n", "\n" + INDENT.repeat(indentLevel + 1));
            result.append(indentedNested.trim());
        } else {
            var itemType = itemsNode.get(TYPE_KEY).asText();
            result.append(itemType).append("[]");
        }
    }

    private static void appendYamlStringField(StringBuilder result, JsonNode node) {
        if (node.has(ENUM_KEY)) {
            result.append(enumSelection(streamElements(node.get(ENUM_KEY))));
        } else {
            result.append(STRING_TYPE);
        }
    }

    private static JsonNode resolveFieldNode(JsonNode node, Map<String, JsonNode> definitions) {
        if (node.has(REF_KEY)) {
            return definitions.get(node.get(REF_KEY).asText());
        }
        if (node.has(ALL_OF_KEY)) {
            return mergeAllOfNodes(node.get(ALL_OF_KEY));
        }
        return node;
    }

    private static JsonNode mergeAllOfNodes(JsonNode allOfNode) {
        var elements = allOfNode.elements();
        var merged = (ObjectNode) elements.next();
        while (elements.hasNext()) {
            merged.setAll((ObjectNode) elements.next());
        }
        return merged;
    }

    private static Stream<Map.Entry<String, JsonNode>> streamFields(JsonNode node) {
        return node == null ? Stream.empty() : StreamSupport.stream(
                Spliterators.spliteratorUnknownSize(node.fields(), Spliterator.ORDERED), false);
    }

    private static Stream<JsonNode> streamElements(JsonNode node) {
        return node == null ? Stream.empty() : StreamSupport.stream(
                Spliterators.spliteratorUnknownSize(node.elements(), Spliterator.ORDERED), false);
    }
}