package spring.ai.schema;

import java.util.stream.Collector;
import java.util.stream.Collectors;

public enum SchemaFormat {
    
    /**
     * Standard JSON schema format.
     */
    JSON_RFC(",\n", "[\n", "\n]"),
    
    /**
     * YAML document schema format
     */
    YAML("\n---\n", "```yaml\n", "\n```"),

    /**
     * JSON
     */
    JSON(",\n", "[\n", "\n]"),;

    private final String delimiter;
    private final String suffix;
    private final String prefix;
    
    private SchemaFormat(String delimiter, String prefix, String suffix) {
        this.delimiter = delimiter;
        this.suffix = suffix;
        this.prefix = prefix;
    }
    
    public Collector<CharSequence, ?, String> collector() {
        return Collectors.joining(delimiter, prefix, suffix);
    }
}
