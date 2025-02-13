/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2025 Ivan Ivanchuk
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included
 * in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NON-INFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package ru.l3r8y.prefixed;

import io.github.classgraph.AnnotationParameterValue;
import io.github.classgraph.ClassGraph;
import io.github.classgraph.ClassInfo;
import io.github.classgraph.ClassInfoList;
import io.github.classgraph.ScanResult;
import one.util.streamex.StreamEx;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import java.io.File;
import java.util.List;
import java.util.Map;

/**
 * Prefixed cop.
 *
 * @since 0.0.0
 */
@Mojo(name = "prefixed", defaultPhase = LifecyclePhase.VERIFY)
public final class PrefixedCop extends AbstractMojo {

    /**
     * The project.
     */
    @Parameter(defaultValue = "${project}")
    private MavenProject project;

    /**
     * The fail on error.
     *
     * @checkstyle MemberNameCheck (6 lines).
     */
    @SuppressWarnings("PMD.ImmutableField")
    @Parameter(defaultValue = "true")
    private boolean failOnError = true;

    @Override
    public void execute() throws MojoExecutionException {
        final String directory = this.project.getBuild().getOutputDirectory();
        final File output = new File(directory);
        if (!output.exists()) {
            this.getLog()
                .info(String.format("Output directory does not exist: '%s'", directory));
        }
        this.getLog().info(String.format("Enforcing @Prefixed naming conventions in: '%s'", directory));
        final ClassGraph graph = new ClassGraph()
            .acceptPaths(directory)
            .enableClassInfo()
            .enableAnnotationInfo();
        try (final ScanResult result = graph.scan()) {
            final ClassInfoList classes = result.getAllClasses();
            if (classes.isEmpty()) {
                this.getLog().info("No classes found, skipping phase...");
                return;
            }
            final List<String> errors = StreamEx.of(classes)
                .filter(ClassInfo::isInterface)
                .cross(info -> StreamEx.of(info.getAnnotationInfo()).filter(it -> "Prefixed".equals(it.getName())))
                .mapKeyValue(
                    (interfaze, anno) -> {
                        final AnnotationParameterValue prefixParameter = anno.getParameterValues().get("prefix");
                        if (prefixParameter == null) {
                            final String message = String.format(
                                "'%s' marked with @Prefixed annotation, but no prefix parameter found",
                                interfaze.getName()
                            );
                            if (this.failOnError) {
                                throw new IllegalStateException(message);
                            }
                            this.getLog().warn(message);
                            return null;
                        }
                        return Map.entry(interfaze, prefixParameter.getValue().toString());
                    }
                ).nonNull()
                .flatMap(interfaceToPrefix ->
                    StreamEx.of(result.getSubclasses(interfaceToPrefix.getKey().getName())
                        .filter(it -> !it.isInterface())
                        .stream()
                        .map(it -> Map.entry(it, Map.entry(interfaceToPrefix.getKey(), interfaceToPrefix.getValue())))
                    )
                ).filter(triple -> !triple.getKey().getName().startsWith(triple.getValue().getValue()))
                .map(triple ->
                    String.format(
                        "Class '%s' implements interface '%s' but misses prefix '%s'",
                        triple.getKey().getName(),
                        triple.getValue().getKey().getSimpleName(),
                        triple.getValue().getValue()
                    )
                ).peek(this.getLog()::error)
                .toList();
            if (!errors.isEmpty()) {
                final String message = String.join("\n", errors);
                if (this.failOnError) {
                    throw new MojoExecutionException(String.format("%d prefix violations found:%%n%s", errors.size(), message));
                }
                else {
                    this.getLog().warn(String.format("%d prefix violations (non-fatal):%%n%s", errors.size(), message));
                }
            }
        }
    }
}
