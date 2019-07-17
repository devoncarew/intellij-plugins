package tanvd.grazi.language

import org.languagetool.rules.Rule

enum class LangToolFixes(private val ruleId: String, private val fix: (suggestion: String) -> String) {
    ARTICLE_MISSING("ARTICLE_MISSING", {
        val suggestion = it.replace("\\s+".toRegex()," ")
        when {
            suggestion.startsWith("The") -> "The " + suggestion[4].toLowerCase() + suggestion.substring(5)
            suggestion.startsWith("A") -> "A " + suggestion[2].toLowerCase() + suggestion.substring(3)
            else -> suggestion
        }
    });

    companion object {
        fun fixSuggestion(ruleId: String, suggestion: String) = values().find { it.ruleId == ruleId }?.let { it.fix(suggestion) } ?: suggestion
        fun fixSuggestion(rule: Rule, suggestion: String) = fixSuggestion(rule.id, suggestion)
    }
}
