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
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import ru.l3r8y.prefixed.annotation.Prefixed;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Prefixed cop.
 * This class designed with focus on performance, this is
 * why code may look ugly to you.
 *
 * @since 0.0.0
 */
@Mojo(name = "prefixed", defaultPhase = LifecyclePhase.VERIFY)
public final class PrefixedCop extends AbstractMojo {

    /**
     * Thread pool to scan project.
     */
    private static final ExecutorService EXECUTORS = Executors.newVirtualThreadPerTaskExecutor();

    /**
     * The project.
     */
    @Parameter(defaultValue = "${project}")
    private MavenProject project;

    /**
     * The base package.
     */
    @Parameter(property = "basePackage", defaultValue = "${project.groupId}")
    private String basePackage;

    /**
     * Fail on error flag.
     */
    @SuppressWarnings("PMD.ImmutableField")
    @Parameter(defaultValue = "true")
    private boolean failOnError = true;

    @Override
    public void execute() throws MojoExecutionException {
        final String outputDir = this.project.getBuild().getOutputDirectory();
        final File output = new File(outputDir);
        if (!output.exists()) {
            this.getLog().info(String.format("Output directory does not exist: '%s'", outputDir));
            return;
        }
        this.getLog().info(String.format("Scanning compiled classes in: '%s'", outputDir));
        final ClassGraph graph = new ClassGraph()
            .overrideClasspath(outputDir)
            .acceptPackages(this.basePackage)
            .enableClassInfo()
            .enableAnnotationInfo();
        try (final ScanResult scanResult = graph.scan(PrefixedCop.EXECUTORS, Runtime.getRuntime().availableProcessors())) {
            final List<ClassInfo> prefixedInterfaces = scanResult
                .getClassesWithAnnotation(Prefixed.class)
                .stream()
                .filter(ClassInfo::isInterface)
                .toList();
            this.getLog().info("Total interfaces found: " + prefixedInterfaces.size());
            final List<String> errors = new ArrayList<>(0);
            for (final ClassInfo iface : prefixedInterfaces) {
                final AnnotationInfo prefixedAnno = iface.getAnnotationInfo().get(Prefixed.class.getName());
                final AnnotationParameterValue prefixParam = prefixedAnno.getParameterValues().get("prefix");
                if (prefixParam == null) {
                    this.handleMissingPrefix(iface.getName());
                    continue;
                }
                final String expectedPrefix = prefixParam.getValue().toString();
                final ClassInfoList implClasses = scanResult.getClassesImplementing(iface.getName());
                final List<String> classErrors = implClasses.stream()
                    .parallel()
                    .filter(impl -> !impl.isInterface())
                    .filter(impl -> !impl.getSimpleName().startsWith(expectedPrefix))
                    .map(impl -> this.formatError(impl.getName(), iface.getSimpleName(), expectedPrefix))
                    .toList();
                errors.addAll(classErrors);
                classErrors.forEach(error -> this.getLog().warn(error));
            }
            this.handleErrors(errors);
        }
    }

    private void handleMissingPrefix(final String interfaceName) throws MojoExecutionException {
        final String msg = String.format(
            "Interface '%s' is marked with @Prefixed but no prefix provided",
            interfaceName
        );
        if (this.failOnError) {
            throw new MojoExecutionException(msg);
        } else {
            this.getLog().warn(msg);
        }
    }

    private String formatError(final String className, final String interfaceName, final String prefix) {
        return String.format(
            "Class '%s' implements '%s' but does not start with prefix '%s'",
            className, interfaceName, prefix
        );
    }

    private void handleErrors(final List<String> errors) throws MojoExecutionException {
        if (!errors.isEmpty()) {
            final String message = String.join("\n", errors);
            if (this.failOnError) {
                throw new MojoExecutionException(errors.size() + " prefix violations found:\n" + message);
            } else {
                this.getLog().warn(errors.size() + " prefix violations (non-fatal):\n" + message);
            }
        }
    }
}