package open.ai.mcp.compiler;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
/**
 * MCP (Model Context Protocol) Compiler Annotations
 * 
 * This package provides annotations for defining MCP tools, resources, and prompts
 * that can be processed by the MCP compiler to generate client SDKs and documentation.
 * 
 * The annotations support:
 * - Tool definitions with parameter validation
 * - Resource declarations with metadata
 * - Prompt templates with argument specifications
 * 
 * @see Tools For defining executable tools/functions
 * @see Resource For declaring static resources
 * @see Prompt For creating prompt templates
 */
public @interface mcp {
    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    public @interface Tools {

        String name() default "";
        String description() default "";
        String title() default "";

        @Target(ElementType.PARAMETER)
        @Retention(RetentionPolicy.RUNTIME)
        public @interface Argument {
            String description() default "";
            boolean required() default true;
        }

    }

    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    public @interface Resource {
        String uri();
        String mimeType();
        String name() default "";
        String description() default "";
    }

    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    public @interface Prompt {
        String name() default "";
        String description() default "";

        @Target(ElementType.PARAMETER)
        @Retention(RetentionPolicy.RUNTIME)
        public @interface Argument {
//            String name() default "";
            String description() default "";
            boolean required() default true;
        }
    }


}


