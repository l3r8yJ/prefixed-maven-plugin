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

import io.github.classgraph.AnnotationInfo;
import io.github.classgraph.AnnotationParameterValue;
import io.github.classgraph.ClassGraph;
import io.github.classgraph.ClassInfo;
import io.github.classgraph.ClassInfoList;
import io.github.classgraph.ScanResult;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import ru.l3r8y.prefixed.annotation.RequirePrefix;

/**
 * Prefixed cop.
 * This class is designed with a focus on performance, which is why the code
 * may look a bit unorthodox.
 *
 * @since 0.0.0
 */
@Mojo(name = "enforce", defaultPhase = LifecyclePhase.VERIFY)
public final class PrefixedCop extends AbstractMojo {

    /**
     * Thread pool to scan project.
     */
    private static final ExecutorService EXECUTORS =
        Executors.newVirtualThreadPerTaskExecutor();

    /**
     * The Maven project.
     */
    @Parameter(defaultValue = "${project}")
    private MavenProject project;

    /**
     * The base package.
     */
    @Parameter(property = "basePackage", defaultValue = "${project.groupId}")
    private String basepackage;

    /**
     * Flag indicating whether to fail on error.
     */
    @SuppressWarnings("PMD.ImmutableField")
    @Parameter(property = "failOnError", defaultValue = "true")
    private boolean failonerror = true;

    @Override
    public void execute() throws MojoExecutionException {
        final String directory = this.project.getBuild().getOutputDirectory();
        if (!new File(directory).exists()) {
            throw new MojoExecutionException(
                "Output directory does not exist: '%s'".formatted(
                    directory
                )
            );
        }
        this.getLog().info(
            "Scanning compiled classes in: '%s'".formatted(
                directory
            )
        );
        try (ScanResult scanResult = this.graphOf(directory)
            .scan(
                PrefixedCop.EXECUTORS,
                Runtime.getRuntime().availableProcessors()
            )
        ) {
            final List<ClassInfo> prefixed = scanResult
                .getClassesWithAnnotation(RequirePrefix.class)
                .stream()
                .filter(ClassInfo::isInterface)
                .toList();
            this.getLog().info(
                "Total interfaces found: %d".formatted(
                    prefixed.size()
                )
            );
            final Collection<String> errors = new ArrayList<>(0);
            for (final ClassInfo iface : prefixed) {
                final AnnotationInfo annotation =
                    iface.getAnnotationInfo().get(RequirePrefix.class.getName());
                final AnnotationParameterValue parameter =
                    annotation.getParameterValues().get("prefix");
                if (parameter == null) {
                    this.handleMissingPrefix(iface.getName());
                    continue;
                }
                final String prefix = parameter.getValue().toString();
                final ClassInfoList impls =
                    scanResult.getClassesImplementing(iface.getName());
                final List<String> violations = impls.parallelStream()
                    .filter(it -> !it.isInterface())
                    .filter(it -> !it.getSimpleName().startsWith(prefix))
                    .map(
                        it ->
                            PrefixedCop.formatError(
                                it.getName(),
                                iface.getName(),
                                prefix
                            )
                    ).toList();
                errors.addAll(violations);
                violations.forEach(this.getLog()::warn);
            }
            this.handleErrors(errors);
        }
    }

    /**
     * Configures class graph with directory.
     *
     * @param directory Directory to scan.
     * @return Configured class graph.
     */
    private ClassGraph graphOf(final String directory) {
        return new ClassGraph()
            .overrideClasspath(directory)
            .acceptPackages(this.basepackage)
            .enableClassInfo()
            .enableAnnotationInfo();
    }

    private void handleMissingPrefix(final String interfaze)
        throws MojoExecutionException {
        final String msg = "Interface '%s' is marked with %s but no prefix provided"
            .formatted(
                interfaze,
                RequirePrefix.class.getSimpleName()
            );
        if (this.failonerror) {
            throw new MojoExecutionException(msg);
        } else {
            this.getLog().warn(msg);
        }
    }

    private static String formatError(
        final String clazz,
        final String interfaze,
        final String prefix
    ) {
        return "Class '%s' implements '%s' but does not start with prefix '%s'"
            .formatted(
                clazz,
                interfaze,
                prefix
            );
    }

    private void handleErrors(final Collection<String> errors)
        throws MojoExecutionException {
        if (!errors.isEmpty()) {
            final String message = String.join("\n", errors);
            if (this.failonerror) {
                throw new MojoExecutionException(
                    "%d prefix violations found:%n%s".formatted(
                        errors.size(),
                        message
                    )
                );
            } else {
                this.getLog().warn(
                    "%d prefix violations :%n%s".formatted(
                        errors.size(),
                        message
                    )
                );
            }
        }
    }
}
