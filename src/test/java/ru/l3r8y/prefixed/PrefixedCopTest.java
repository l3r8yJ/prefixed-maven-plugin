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
/*
 * @todo:8:60min Rewrite test in invoker or setup these.
 *   In current condition tests just don't work properly,
 *   We have two options here:
 *   1. Migration to invoker plugin and stay only with integration tests.
 *   2. Setup `Farea` properly and use it WITH invoker test projects.
 * */
package ru.l3r8y.prefixed;

import com.yegor256.Mktmp;
import com.yegor256.MktmpResolver;
import com.yegor256.farea.Farea;
import java.io.IOException;
import java.nio.file.Path;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Tests for {@link PrefixedCop}.
 *
 * @since 0.0.0
 */
@ExtendWith(MktmpResolver.class)
final class PrefixedCopTest {

    @Test
    @Disabled
    void printsLogs(@Mktmp final Path temp) throws IOException {
        new Farea(temp).together(
            f -> {
                f.clean();
                f.build()
                    .plugins()
                    .appendItself()
                    .execution()
                    .goals("enforce");
                f.exec("verify");
                MatcherAssert.assertThat(
                    "Lints URL was not printed, but it should",
                    f.log().content(),
                    Matchers.containsString(
                        "[INFO] Enforcing @Prefixed naming conventions in:"
                    )
                );
            }
        );
    }
}
