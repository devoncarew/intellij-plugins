// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.vuejs.inspections

import com.intellij.codeInspection.InspectionProfileEntry
import com.intellij.lang.javascript.highlighting.IntentionAndInspectionFilter
import com.sixrr.inspectjs.validity.BadExpressionStatementJSInspection

class VueInspectionFilter : IntentionAndInspectionFilter() {
  override fun isSupportedInspection(inspectionToolId: String?): Boolean =
    inspectionToolId != InspectionProfileEntry.getShortName(BadExpressionStatementJSInspection::class.java.simpleName)
}
