package com.inovexcorp.queryservice.app.security;

import com.inovexcorp.queryservice.security.QttRoles;
import io.quarkus.security.Authenticated;
import jakarta.annotation.security.DenyAll;
import jakarta.annotation.security.PermitAll;
import jakarta.annotation.security.RolesAllowed;
import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;
import org.jboss.jandex.Index;
import org.jboss.jandex.IndexReader;
import org.jboss.jandex.Indexer;
import org.jboss.jandex.MethodInfo;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Checks every REST method in the application against security/role-table.txt: each must name exactly the role the
 * table gives it. Catches a new endpoint without a role (Quarkus would deny it at runtime, which surfaces as a 403 in
 * production rather than a failed build) and an accidental change to an existing endpoint's role.
 */
class RoleTableTest {

    private static final DotName PATH = DotName.createSimple("jakarta.ws.rs.Path");
    private static final List<String> HTTP_METHODS = List.of("GET", "POST", "PUT", "DELETE", "PATCH", "HEAD", "OPTIONS");
    private static final Map<String, String> ROLE_NAMES = Map.of(
            QttRoles.VIEWER, "viewer", QttRoles.EDITOR, "editor", QttRoles.ADMIN, "admin");

    @Test
    void everyRestMethodHasTheRoleTheTableGivesIt() throws IOException {
        Map<String, String> expected = readTable();
        Map<String, String> actual = scanResources();

        List<String> problems = new ArrayList<>();
        for (Map.Entry<String, String> e : actual.entrySet()) {
            String want = expected.get(e.getKey());
            if (want == null) {
                problems.add("not in role-table.txt: " + e.getKey() + " (annotated " + e.getValue() + ")");
            } else if (!want.equals(e.getValue())) {
                problems.add("role mismatch: " + e.getKey() + " table=" + want + " code=" + e.getValue());
            }
        }
        for (String key : new TreeSet<>(expected.keySet())) {
            if (!actual.containsKey(key)) {
                problems.add("in role-table.txt but no such REST method: " + key);
            }
        }
        assertTrue(problems.isEmpty(), String.join("\n", problems));
        assertTrue(actual.size() > 50, "expected the full API, found " + actual.size() + " methods");
    }

    /** "METHOD /path" -> role, for every resource method in the indexed QTT modules and the application. */
    private static Map<String, String> scanResources() throws IOException {
        Map<String, String> found = new TreeMap<>();
        for (Index index : indexes()) {
            for (AnnotationInstance classPath : index.getAnnotations(PATH)) {
                if (classPath.target().kind() != org.jboss.jandex.AnnotationTarget.Kind.CLASS) {
                    continue;
                }
                ClassInfo cls = classPath.target().asClass();
                if (!cls.name().toString().startsWith("com.inovexcorp.queryservice")) {
                    continue;
                }
                for (MethodInfo method : cls.methods()) {
                    String verb = HTTP_METHODS.stream()
                            .filter(v -> method.hasDeclaredAnnotation(DotName.createSimple("jakarta.ws.rs." + v)))
                            .findFirst().orElse(null);
                    if (verb == null) {
                        continue;
                    }
                    AnnotationInstance methodPath = method.declaredAnnotation(PATH);
                    String path = join(classPath.value().asString(), methodPath == null ? "" : methodPath.value().asString());
                    found.put(verb + " " + path, roleOf(method, cls));
                }
            }
        }
        return found;
    }

    private static String roleOf(MethodInfo method, ClassInfo cls) {
        for (AnnotationInstance a : new AnnotationInstance[]{
                method.declaredAnnotation(DotName.createSimple(RolesAllowed.class.getName())),
                cls.declaredAnnotation(DotName.createSimple(RolesAllowed.class.getName()))}) {
            if (a != null) {
                String[] roles = a.value().asStringArray();
                return roles.length == 1 && ROLE_NAMES.containsKey(roles[0])
                        ? ROLE_NAMES.get(roles[0]) : "roles" + List.of(roles);
            }
        }
        if (method.hasDeclaredAnnotation(DotName.createSimple(Authenticated.class.getName()))) {
            return "authenticated";
        }
        if (method.hasDeclaredAnnotation(DotName.createSimple(PermitAll.class.getName()))) {
            return "permit-all";
        }
        if (method.hasDeclaredAnnotation(DotName.createSimple(DenyAll.class.getName()))) {
            return "deny-all";
        }
        return "<none>";
    }

    private static String join(String a, String b) {
        String path = ("/" + a + "/" + b).replaceAll("/+", "/");
        return path.length() > 1 && path.endsWith("/") ? path.substring(0, path.length() - 1) : path;
    }

    /** The Jandex indexes of the QTT library jars, plus the application's own resource classes. */
    private static List<Index> indexes() throws IOException {
        List<Index> result = new ArrayList<>();
        Enumeration<URL> urls = RoleTableTest.class.getClassLoader().getResources("META-INF/jandex.idx");
        while (urls.hasMoreElements()) {
            URL url = urls.nextElement();
            if (url.toString().contains("query-service") || url.toString().contains("camel-anzo")) {
                try (InputStream in = url.openStream()) {
                    result.add(new IndexReader(in).read());
                }
            }
        }
        assertTrue(result.size() >= 5, "expected the QTT modules' Jandex indexes on the test classpath, found " + result.size());
        Indexer app = new Indexer();
        app.indexClass(MeResource.class);
        app.indexClass(SsoInfoResource.class);
        app.indexClass(ApiKeysResource.class);
        result.add(app.complete());
        return result;
    }

    private static Map<String, String> readTable() throws IOException {
        Map<String, String> table = new TreeMap<>();
        try (InputStream in = RoleTableTest.class.getResourceAsStream("/security/role-table.txt");
             BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) {
                line = line.strip();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                String[] parts = line.split("\\s+");
                assertEquals(3, parts.length, "bad role-table.txt line: " + line);
                String previous = table.put(parts[0] + " " + join(parts[1], ""), parts[2]);
                assertEquals(null, previous, "duplicate role-table.txt line: " + line);
            }
        }
        return table;
    }
}
