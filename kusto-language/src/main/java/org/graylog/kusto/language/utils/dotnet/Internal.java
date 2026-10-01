// Original to kusto-java (no upstream file). SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 Graylog, Inc. Purpose: marks members that are internal upstream (PORTING.md 2.6).

package org.graylog.kusto.language.utils.dotnet;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Marks members that are {@code internal} upstream (PORTING.md 2.6). */
@Retention(RetentionPolicy.CLASS)
@Documented
@Target({ElementType.TYPE, ElementType.METHOD, ElementType.FIELD, ElementType.CONSTRUCTOR})
public @interface Internal {
}
