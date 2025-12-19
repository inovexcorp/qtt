package com.inovexcorp.queryservice.testing.util;

import com.inovexcorp.queryservice.testing.model.TemplateVariable;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

/**
 * Comprehensive unit tests for FreemarkerVariableExtractor
 */
class FreemarkerVariableExtractorTest {

    // ========================================
    // VARIABLE EXTRACTION TESTS (25 tests)
    // ========================================

    @Test
    void extractVariables_simpleInterpolation_shouldExtractVariable() {
        String template = "SELECT ?s WHERE { ?s <prop> ${userId} }";

        List<TemplateVariable> variables = FreemarkerVariableExtractor.extractVariables(template);

        assertThat(variables).hasSize(1);
        assertThat(variables.get(0).getName()).isEqualTo("userId");
        assertThat(variables.get(0).getDefaultValue()).isNull();
        assertThat(variables.get(0).getType()).isEqualTo("interpolation");
    }

    @Test
    void extractVariables_interpolationWithDefault_shouldExtractVariableAndDefault() {
        String template = "LIMIT ${limit!100}";

        List<TemplateVariable> variables = FreemarkerVariableExtractor.extractVariables(template);

        assertThat(variables).hasSize(1);
        assertThat(variables.get(0).getName()).isEqualTo("limit");
        assertThat(variables.get(0).getDefaultValue()).isEqualTo("100");
        assertThat(variables.get(0).getType()).isEqualTo("interpolation");
    }

    @Test
    void extractVariables_multipleInterpolations_shouldExtractAll() {
        String template = "${var1} ${var2} ${var3}";

        List<TemplateVariable> variables = FreemarkerVariableExtractor.extractVariables(template);

        assertThat(variables).hasSize(3);
        assertThat(variables).extracting(TemplateVariable::getName)
                .containsExactly("var1", "var2", "var3");
    }

    @Test
    void extractVariables_duplicateVariables_shouldReturnUnique() {
        String template = "${userId} and ${userId} and ${userId}";

        List<TemplateVariable> variables = FreemarkerVariableExtractor.extractVariables(template);

        assertThat(variables).hasSize(1);
        assertThat(variables.get(0).getName()).isEqualTo("userId");
    }

    @Test
    void extractVariables_variableWithDots_shouldExtractFully() {
        String template = "${user.profile.name}";

        List<TemplateVariable> variables = FreemarkerVariableExtractor.extractVariables(template);

        assertThat(variables).hasSize(1);
        assertThat(variables.get(0).getName()).isEqualTo("user.profile.name");
        assertThat(variables.get(0).getType()).isEqualTo("interpolation");
    }

    @Test
    void extractVariables_variableWithUnderscore_shouldExtract() {
        String template = "${user_id} ${max_results}";

        List<TemplateVariable> variables = FreemarkerVariableExtractor.extractVariables(template);

        assertThat(variables).hasSize(2);
        assertThat(variables).extracting(TemplateVariable::getName)
                .containsExactly("user_id", "max_results");
    }

    @Test
    void extractVariables_requestParamDoubleQuotes_shouldExtractAsRequestParam() {
        String template = "?offset=${Request[\"offset\"]}";

        List<TemplateVariable> variables = FreemarkerVariableExtractor.extractVariables(template);

        assertThat(variables).hasSize(1);
        assertThat(variables.get(0).getName()).isEqualTo("offset");
        assertThat(variables.get(0).getType()).isEqualTo("request_param");
        assertThat(variables.get(0).getDefaultValue()).isNull();
    }

    @Test
    void extractVariables_requestParamSingleQuotes_shouldExtractAsRequestParam() {
        String template = "?limit=${Request['limit']}";

        List<TemplateVariable> variables = FreemarkerVariableExtractor.extractVariables(template);

        assertThat(variables).hasSize(1);
        assertThat(variables.get(0).getName()).isEqualTo("limit");
        assertThat(variables.get(0).getType()).isEqualTo("request_param");
    }

    @Test
    void extractVariables_multipleRequestParams_shouldExtractAll() {
        String template = "${Request[\"param1\"]} ${Request['param2']} ${Request[\"param3\"]}";

        List<TemplateVariable> variables = FreemarkerVariableExtractor.extractVariables(template);

        assertThat(variables).hasSize(3);
        assertThat(variables).extracting(TemplateVariable::getName)
                .containsExactlyInAnyOrder("param1", "param2", "param3");
        assertThat(variables).extracting(TemplateVariable::getType)
                .containsOnly("request_param");
    }

    @Test
    void extractVariables_requestVariableItself_shouldBeSkipped() {
        String template = "${Request}";

        List<TemplateVariable> variables = FreemarkerVariableExtractor.extractVariables(template);

        assertThat(variables).isEmpty();
    }

    @Test
    void extractVariables_mixedTypes_shouldExtractBoth() {
        String template = "${userId} and ${Request[\"sessionId\"]} and ${limit!50}";

        List<TemplateVariable> variables = FreemarkerVariableExtractor.extractVariables(template);

        assertThat(variables).hasSize(3);
        assertThat(variables).extracting(TemplateVariable::getName)
                .containsExactlyInAnyOrder("userId", "sessionId", "limit");
        // Check that we have both types
        long interpolationCount = variables.stream().filter(v -> "interpolation".equals(v.getType())).count();
        long requestParamCount = variables.stream().filter(v -> "request_param".equals(v.getType())).count();
        assertThat(interpolationCount).isEqualTo(2);
        assertThat(requestParamCount).isEqualTo(1);
    }

    @Test
    void extractVariables_nullTemplate_shouldReturnEmpty() {
        List<TemplateVariable> variables = FreemarkerVariableExtractor.extractVariables(null);

        assertThat(variables).isEmpty();
    }

    @Test
    void extractVariables_emptyTemplate_shouldReturnEmpty() {
        List<TemplateVariable> variables = FreemarkerVariableExtractor.extractVariables("");

        assertThat(variables).isEmpty();
    }

    @Test
    void extractVariables_noVariables_shouldReturnEmpty() {
        String template = "SELECT ?s WHERE { ?s <prop> <value> }";

        List<TemplateVariable> variables = FreemarkerVariableExtractor.extractVariables(template);

        assertThat(variables).isEmpty();
    }

    @Test
    void extractVariables_defaultWithSpecialChars_shouldExtractCorrectly() {
        String template = "${uri!\"http://example.com\"}";

        List<TemplateVariable> variables = FreemarkerVariableExtractor.extractVariables(template);

        assertThat(variables).hasSize(1);
        assertThat(variables.get(0).getName()).isEqualTo("uri");
        assertThat(variables.get(0).getDefaultValue()).isEqualTo("\"http://example.com\"");
    }

    @Test
    void extractVariables_defaultWithSpaces_shouldExtractCorrectly() {
        String template = "${name!'John Doe'}";

        List<TemplateVariable> variables = FreemarkerVariableExtractor.extractVariables(template);

        assertThat(variables).hasSize(1);
        assertThat(variables.get(0).getName()).isEqualTo("name");
        assertThat(variables.get(0).getDefaultValue()).isEqualTo("'John Doe'");
    }

    @Test
    void extractVariables_complexRealWorldTemplate_shouldExtractAllVariables() {
        String template = """
                PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
                SELECT ?s ?p ?o
                WHERE {
                  ?s rdf:type <${entityType!http://example.com/Person}> .
                  ?s ?p ?o .
                  FILTER(?s = <${Request["uri"]}>)
                }
                LIMIT ${limit!10}
                OFFSET ${Request['offset']}
                """;

        List<TemplateVariable> variables = FreemarkerVariableExtractor.extractVariables(template);

        assertThat(variables).hasSize(4);
        assertThat(variables).extracting(TemplateVariable::getName)
                .containsExactlyInAnyOrder("entityType", "uri", "limit", "offset");
    }

    @Test
    void extractVariables_preservesOrder_shouldReturnInOrder() {
        String template = "${z} ${a} ${m}";

        List<TemplateVariable> variables = FreemarkerVariableExtractor.extractVariables(template);

        assertThat(variables).extracting(TemplateVariable::getName)
                .containsExactly("z", "a", "m");
    }

    @Test
    void extractVariables_duplicatesWithDifferentDefaults_shouldKeepBoth() {
        // Note: LinkedHashSet uses equals/hashCode, so variables with same name but different
        // defaults are considered different objects and both are kept
        String template = "${limit!10} and ${limit!20}";

        List<TemplateVariable> variables = FreemarkerVariableExtractor.extractVariables(template);

        assertThat(variables).hasSize(2);
        assertThat(variables).extracting(TemplateVariable::getName)
                .containsExactly("limit", "limit");
        assertThat(variables).extracting(TemplateVariable::getDefaultValue)
                .containsExactly("10", "20");
    }

    @Test
    void extractVariables_requestParamWithDuplicates_shouldReturnUnique() {
        String template = "${Request[\"id\"]} and ${Request['id']}";

        List<TemplateVariable> variables = FreemarkerVariableExtractor.extractVariables(template);

        assertThat(variables).hasSize(1);
        assertThat(variables.get(0).getName()).isEqualTo("id");
    }

    @Test
    void extractVariables_numbersInVariableName_shouldExtract() {
        String template = "${var1} ${param2test} ${abc123def}";

        List<TemplateVariable> variables = FreemarkerVariableExtractor.extractVariables(template);

        assertThat(variables).hasSize(3);
        assertThat(variables).extracting(TemplateVariable::getName)
                .containsExactly("var1", "param2test", "abc123def");
    }

    @Test
    void extractVariables_variableStartingWithUnderscore_shouldExtract() {
        String template = "${_privateVar} ${_id}";

        List<TemplateVariable> variables = FreemarkerVariableExtractor.extractVariables(template);

        assertThat(variables).hasSize(2);
        assertThat(variables).extracting(TemplateVariable::getName)
                .containsExactly("_privateVar", "_id");
    }

    @Test
    void extractVariables_defaultWithBooleanValue_shouldExtract() {
        String template = "${enabled!true} ${disabled!false}";

        List<TemplateVariable> variables = FreemarkerVariableExtractor.extractVariables(template);

        assertThat(variables).hasSize(2);
        assertThat(variables.get(0).getDefaultValue()).isEqualTo("true");
        assertThat(variables.get(1).getDefaultValue()).isEqualTo("false");
    }

    @Test
    void extractVariables_defaultWithDecimalNumber_shouldExtract() {
        String template = "${price!99.99}";

        List<TemplateVariable> variables = FreemarkerVariableExtractor.extractVariables(template);

        assertThat(variables).hasSize(1);
        assertThat(variables.get(0).getDefaultValue()).isEqualTo("99.99");
    }

    @Test
    void extractVariables_requestParamWithUnderscoreAndNumbers_shouldExtract() {
        String template = "${Request[\"user_id_123\"]}";

        List<TemplateVariable> variables = FreemarkerVariableExtractor.extractVariables(template);

        assertThat(variables).hasSize(1);
        assertThat(variables.get(0).getName()).isEqualTo("user_id_123");
    }

    // ========================================
    // JSON STRUCTURE BUILDING TESTS (15 tests)
    // ========================================

    @Test
    void extractBodyJsonStructure_nullTemplate_shouldReturnEmpty() {
        Map<String, Object> structure = FreemarkerVariableExtractor.extractBodyJsonStructure(null);

        assertThat(structure).isEmpty();
    }

    @Test
    void extractBodyJsonStructure_emptyTemplate_shouldReturnEmpty() {
        Map<String, Object> structure = FreemarkerVariableExtractor.extractBodyJsonStructure("");

        assertThat(structure).isEmpty();
    }

    @Test
    void extractBodyJsonStructure_noBodyReferences_shouldReturnEmpty() {
        String template = "${userId} ${Request[\"param\"]}";

        Map<String, Object> structure = FreemarkerVariableExtractor.extractBodyJsonStructure(template);

        assertThat(structure).isEmpty();
    }

    @Test
    void extractBodyJsonStructure_directBodyReference_shouldCreatePlaceholder() {
        String template = "SELECT * WHERE { ?s <prop> ${body.name} }";

        Map<String, Object> structure = FreemarkerVariableExtractor.extractBodyJsonStructure(template);

        assertThat(structure).containsOnly(entry("name", "<name>"));
    }

    @Test
    void extractBodyJsonStructure_multipleDirectBodyReferences_shouldCreateFlatStructure() {
        String template = "${body.name} ${body.email} ${body.age}";

        Map<String, Object> structure = FreemarkerVariableExtractor.extractBodyJsonStructure(template);

        assertThat(structure).containsOnly(
                entry("name", "<name>"),
                entry("email", "<email>"),
                entry("age", "<age>")
        );
    }

    @Test
    void extractBodyJsonStructure_assignedVariable_shouldExtractPaths() {
        String template = """
                <#assign data=body?eval_json>
                ${data.firstName}
                ${data.lastName}
                """;

        Map<String, Object> structure = FreemarkerVariableExtractor.extractBodyJsonStructure(template);

        assertThat(structure).containsOnly(
                entry("firstName", "<firstName>"),
                entry("lastName", "<lastName>")
        );
    }

    @Test
    void extractBodyJsonStructure_nestedPaths_shouldCreateNestedStructure() {
        String template = """
                <#assign data=body?eval_json>
                ${data.user.name}
                ${data.user.email}
                """;

        Map<String, Object> structure = FreemarkerVariableExtractor.extractBodyJsonStructure(template);

        assertThat(structure).containsKey("user");
        @SuppressWarnings("unchecked")
        Map<String, Object> user = (Map<String, Object>) structure.get("user");
        assertThat(user).containsOnly(
                entry("name", "<name>"),
                entry("email", "<email>")
        );
    }

    @Test
    void extractBodyJsonStructure_deeplyNestedPaths_shouldCreateDeepStructure() {
        String template = """
                <#assign data=body?eval_json>
                ${data.level1.level2.level3.value}
                """;

        Map<String, Object> structure = FreemarkerVariableExtractor.extractBodyJsonStructure(template);

        assertThat(structure).containsKey("level1");
        @SuppressWarnings("unchecked")
        Map<String, Object> level1 = (Map<String, Object>) structure.get("level1");
        assertThat(level1).containsKey("level2");
        @SuppressWarnings("unchecked")
        Map<String, Object> level2 = (Map<String, Object>) level1.get("level2");
        assertThat(level2).containsKey("level3");
        @SuppressWarnings("unchecked")
        Map<String, Object> level3 = (Map<String, Object>) level2.get("level3");
        assertThat(level3).containsOnly(entry("value", "<value>"));
    }

    @Test
    void extractBodyJsonStructure_mixedFlatAndNested_shouldCombineCorrectly() {
        String template = """
                <#assign data=body?eval_json>
                ${data.name}
                ${data.address.street}
                ${data.address.city}
                ${data.email}
                """;

        Map<String, Object> structure = FreemarkerVariableExtractor.extractBodyJsonStructure(template);

        assertThat(structure).containsKeys("name", "address", "email");
        assertThat(structure.get("name")).isEqualTo("<name>");
        assertThat(structure.get("email")).isEqualTo("<email>");

        @SuppressWarnings("unchecked")
        Map<String, Object> address = (Map<String, Object>) structure.get("address");
        assertThat(address).containsOnly(
                entry("street", "<street>"),
                entry("city", "<city>")
        );
    }

    @Test
    void extractBodyJsonStructure_defaultValueString_shouldUseStringValue() {
        String template = "${body.name!'John Doe'}";

        Map<String, Object> structure = FreemarkerVariableExtractor.extractBodyJsonStructure(template);

        assertThat(structure).containsOnly(entry("name", "John Doe"));
    }

    @Test
    void extractBodyJsonStructure_defaultValueNumber_shouldParseAsNumber() {
        String template = "${body.count!42} ${body.price!19.99}";

        Map<String, Object> structure = FreemarkerVariableExtractor.extractBodyJsonStructure(template);

        assertThat(structure).containsOnly(
                entry("count", 42),
                entry("price", 19.99)
        );
    }

    @Test
    void extractBodyJsonStructure_defaultValueBoolean_shouldParseAsBoolean() {
        String template = "${body.enabled!true} ${body.disabled!false}";

        Map<String, Object> structure = FreemarkerVariableExtractor.extractBodyJsonStructure(template);

        assertThat(structure).containsOnly(
                entry("enabled", true),
                entry("disabled", false)
        );
    }

    @Test
    void extractBodyJsonStructure_defaultValueQuotedString_shouldRemoveQuotes() {
        String template = "${body.uri!\"http://example.com\"}";

        Map<String, Object> structure = FreemarkerVariableExtractor.extractBodyJsonStructure(template);

        assertThat(structure).containsOnly(entry("uri", "http://example.com"));
    }

    @Test
    void extractBodyJsonStructure_excludesHeadersPaths_shouldNotIncludeHeaders() {
        String template = """
                <#assign data=body?eval_json>
                ${data.name}
                ${data.headers.authorization}
                ${data.headers.contentType}
                """;

        Map<String, Object> structure = FreemarkerVariableExtractor.extractBodyJsonStructure(template);

        assertThat(structure).containsOnly(entry("name", "<name>"));
        assertThat(structure).doesNotContainKey("headers");
    }

    @Test
    void extractBodyJsonStructure_directBodyHeadersReference_shouldExclude() {
        String template = "${body.name} ${body.headers.auth}";

        Map<String, Object> structure = FreemarkerVariableExtractor.extractBodyJsonStructure(template);

        assertThat(structure).containsOnly(entry("name", "<name>"));
    }

    @Test
    void extractBodyJsonStructure_conditionalReferences_shouldInclude() {
        String template = """
                <#assign data=body?eval_json>
                <#if data.userId??>
                  ?user <prop> ${data.userId}
                </#if>
                """;

        Map<String, Object> structure = FreemarkerVariableExtractor.extractBodyJsonStructure(template);

        assertThat(structure).containsOnly(entry("userId", "<userId>"));
    }

    @Test
    void extractBodyJsonStructure_conflictingPaths_shouldResolveToMap() {
        // This tests when a path is used both as a leaf value and as an object
        String template = """
                <#assign data=body?eval_json>
                ${data.user}
                ${data.user.name}
                """;

        Map<String, Object> structure = FreemarkerVariableExtractor.extractBodyJsonStructure(template);

        // The nested path takes precedence
        assertThat(structure).containsKey("user");
        Object user = structure.get("user");
        assertThat(user).isInstanceOf(Map.class);
    }

    @Test
    void extractBodyJsonStructure_realWorldComplexTemplate_shouldBuildCorrectStructure() {
        String template = """
                <#assign data=body?eval_json>
                PREFIX ex: <http://example.com/>
                SELECT ?result
                WHERE {
                  ?result ex:name "${data.person.firstName}" ;
                          ex:age ${data.person.age!25} ;
                          ex:email "${data.contact.email}" ;
                          ex:active ${data.settings.enabled!true} .
                }
                """;

        Map<String, Object> structure = FreemarkerVariableExtractor.extractBodyJsonStructure(template);

        assertThat(structure).containsKeys("person", "contact", "settings");

        @SuppressWarnings("unchecked")
        Map<String, Object> person = (Map<String, Object>) structure.get("person");
        assertThat(person).containsOnly(
                entry("firstName", "<firstName>"),
                entry("age", 25)
        );

        @SuppressWarnings("unchecked")
        Map<String, Object> contact = (Map<String, Object>) structure.get("contact");
        assertThat(contact).containsOnly(entry("email", "<email>"));

        @SuppressWarnings("unchecked")
        Map<String, Object> settings = (Map<String, Object>) structure.get("settings");
        assertThat(settings).containsOnly(entry("enabled", true));
    }

    @Test
    void extractBodyJsonStructure_duplicatePaths_shouldNotDuplicate() {
        String template = """
                <#assign data=body?eval_json>
                ${data.name}
                ${data.name}
                ${data.name}
                """;

        Map<String, Object> structure = FreemarkerVariableExtractor.extractBodyJsonStructure(template);

        assertThat(structure).containsOnly(entry("name", "<name>"));
    }

    @Test
    void extractBodyJsonStructure_multipleAssignStatements_shouldUseFirst() {
        String template = """
                <#assign data=body?eval_json>
                ${data.field1}
                <#assign other=body?eval_json>
                ${other.field2}
                """;

        Map<String, Object> structure = FreemarkerVariableExtractor.extractBodyJsonStructure(template);

        // Should extract from 'data' (first assignment)
        assertThat(structure).containsOnly(entry("field1", "<field1>"));
    }

    @Test
    void extractBodyJsonStructure_mixedDirectAndAssignedReferences_shouldCombine() {
        String template = """
                <#assign data=body?eval_json>
                ${body.directField}
                ${data.assignedField}
                """;

        Map<String, Object> structure = FreemarkerVariableExtractor.extractBodyJsonStructure(template);

        assertThat(structure).containsOnly(
                entry("directField", "<directField>"),
                entry("assignedField", "<assignedField>")
        );
    }
}
