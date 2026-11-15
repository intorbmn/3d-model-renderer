import static org.lwjgl.util.shaderc.Shaderc.*;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipFile;
import org.lwjgl.system.MemoryUtil;

/**
 * Compiles shaders to SPIR-V with the shaderc library Minecraft 26.3 ships, the way the game does
 * (GLSL -> SPIR-V), resolving <namespace:file> includes from the Minecraft jar and the mod's resources.
 * Vanilla's entity shader is compiled first as a control.
 *
 * usage: java ShaderCheck <minecraft.jar> <mod resources dir> <file>[:DEFINE[=value],...] ...
 */
public class ShaderCheck {
    static ZipFile jar;
    static Path modResources;

    public static void main(String[] args) throws Exception {
        jar = new ZipFile(args[0]);
        modResources = Path.of(args[1]);
        int failures = 0;
        for (int i = 2; i < args.length; i++) {
            failures += check(args[i]) ? 0 : 1;
        }
        System.out.println(failures == 0 ? "ALL SHADERS COMPILE" : failures + " SHADER(S) FAILED");
        System.exit(failures == 0 ? 0 : 1);
    }

    static String read(String namespace, String path) throws Exception {
        String inJar = "assets/" + namespace + "/shaders/" + path;
        Path mod = modResources.resolve(inJar);
        if (Files.exists(mod)) {
            return Files.readString(mod);
        }
        var entry = jar.getEntry(inJar);
        if (entry == null) {
            throw new IllegalArgumentException("missing shader file " + namespace + ":" + path);
        }
        return new String(jar.getInputStream(entry).readAllBytes(), StandardCharsets.UTF_8);
    }

    static String expand(String source, Set<String> seen) throws Exception {
        Matcher m = Pattern.compile("#include <(\\w+):([\\w./]+)>").matcher(source);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            String key = m.group(1) + ":" + m.group(2);
            String body = seen.add(key) ? expand(read(m.group(1), "include/" + m.group(2)), seen) : "";
            m.appendReplacement(out, Matcher.quoteReplacement(body));
        }
        m.appendTail(out);
        return out.toString();
    }

    static boolean check(String spec) throws Exception {
        String[] parts = spec.split(":", 3);
        String namespace = parts[0];
        String path = parts[1];
        String defines = parts.length > 2 ? parts[2] : "";
        String source = read(namespace, path);
        String[] lines = source.split("\n", 3);
        StringBuilder full = new StringBuilder(lines[0]).append('\n').append(lines[1]).append('\n');
        for (String define : defines.split(",")) {
            if (!define.isBlank()) {
                full.append("#define ").append(define.replace('=', ' ')).append('\n');
            }
        }
        full.append(expand(lines[2], new HashSet<>()));

        int kind = path.endsWith(".vsh") ? shaderc_vertex_shader : shaderc_fragment_shader;
        long compiler = shaderc_compiler_initialize();
        long options = shaderc_compile_options_initialize();
        shaderc_compile_options_set_target_env(options, shaderc_target_env_opengl, shaderc_env_version_opengl_4_5);
        shaderc_compile_options_set_auto_bind_uniforms(options, true);
        shaderc_compile_options_set_auto_map_locations(options, true);
        long result = shaderc_compile_into_spv(compiler, full.toString(), kind, path, "main", options);
        boolean ok = shaderc_result_get_compilation_status(result) == shaderc_compilation_status_success;
        String label = namespace + ":" + path + (defines.isEmpty() ? "" : " [" + defines + "]");
        if (ok) {
            System.out.println("OK   " + label + " (" + shaderc_result_get_length(result) + " bytes of SPIR-V)");
        } else {
            System.out.println("FAIL " + label + "\n" + shaderc_result_get_error_message(result));
        }
        shaderc_result_release(result);
        shaderc_compile_options_release(options);
        shaderc_compiler_release(compiler);
        return ok;
    }
}
