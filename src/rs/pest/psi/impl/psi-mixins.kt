package rs.pest.psi.impl

import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.extapi.psi.ASTWrapperPsiElement
import com.intellij.lang.ASTNode
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.TextRange
import com.intellij.psi.*
import com.intellij.util.IncorrectOperationException
import icons.PestIcons
import rs.pest.PestFile
import rs.pest.psi.*

abstract class PestElement(node: ASTNode) : ASTWrapperPsiElement(node) {
	val containingPestFile get() = containingFile as? PestFile
	protected fun allGrammarRules(): Collection<PestGrammarRuleMixin> = containingPestFile?.rules().orEmpty()
}

fun renameBuiltin(): Nothing = throw IncorrectOperationException("Cannot rename a builtin rule!")

abstract class PestGrammarRuleMixin(node: ASTNode) : PestElement(node) , PsiNameIdentifierOwner , PestGrammarRule{
	private var recCache: Boolean? = null
	val isRecursive get() = recCache ?: run {
		val grammarBody = grammarBody?.expression ?: return@run false
		val name = name
		SyntaxTraverser
			.psiTraverser(grammarBody)
			.filterTypes { it == PestTypes.IDENTIFIER }
			.any { it.text == name }
	}.also { recCache = it }

	private var refCache: MutableList<PsiReference>? = null
	private fun checkedRefCache() = refCache?.apply { removeAll { !it.element.isValid } }?.toTypedArray()
	fun refreshReferenceCache() = refreshReferenceCache(name, nameIdentifier)
	private fun refreshReferenceCache(myName: String, self: PsiElement) = collectFrom<PestIdentifier>(containingFile, myName, self).also { refCache = it }
	override fun subtreeChanged() {
		refreshReferenceCache()
		typeCache = null
		recCache = null
	}

	override fun getNameIdentifier(): PsiElement = firstChild
	override fun getIcon(flags: Int) = PestIcons.PEST
	override fun getName(): String = nameIdentifier.text
	override fun setName(newName: String) : PsiElement {
		firstChild.replace(PestTokenType.createRuleName(newName, project))
		return this
	}


	fun preview(maxSizeExpected: Int) = grammarBody?.expression?.bodyText(maxSizeExpected)
	private var typeCache: PestRuleType? = null
	val type: PestRuleType
		get() = typeCache ?: when (modifier?.firstChild?.node?.elementType) {
			PestTypes.SILENT_MODIFIER -> PestRuleType.Silent
			PestTypes.ATOMIC_MODIFIER -> PestRuleType.Atomic
			PestTypes.NON_ATOMIC_MODIFIER -> PestRuleType.NonAtomic
			PestTypes.COMPOUND_ATOMIC_MODIFIER -> PestRuleType.CompoundAtomic
			else -> PestRuleType.Simple
		}.also { typeCache = it }
}


abstract class PestResolvableMixin(node: ASTNode) : PestExpressionImpl(node), PsiPolyVariantReference {
	private val range = TextRange(0, textLength)
	override fun isSoft() = true
	override fun getRangeInElement() = range

	override fun getReference() = this
	override fun getReferences() = arrayOf(reference)
	override fun isReferenceTo(reference: PsiElement) = reference == resolve()
	override fun getCanonicalText(): String = text
	override fun resolve(): PsiElement? = multiResolve(false).firstOrNull()?.run { element }
	override fun multiResolve(incomplete: Boolean): Array<ResolveResult> = allGrammarRules().filter { it.name == text }.map(::PsiElementResolveResult).toTypedArray()
	override fun getElement() = this
	override fun bindToElement(element: PsiElement): PsiElement = throw IncorrectOperationException("Unsupported")
	override fun getVariants() = allGrammarRules().map {
		LookupElementBuilder
			.create(it)
			.withTailText(it.preview(35), true)
			.withIcon(it.getIcon(0))
			.withTypeText(it.type.description)
	}.toTypedArray()
}


abstract class PestIdentifierMixin(node: ASTNode) : PestResolvableMixin(node) {
	override fun handleElementRename(newName: String): PsiElement = replace(PestTokenType.createExpression(newName, project))
}

abstract class PestBuiltinMixin(node: ASTNode) : PestResolvableMixin(node) {
	override fun handleElementRename(newName: String): PsiElement = replace(PestTokenType.createExpression(newName, project))
}

abstract class PestStringMixin(node: ASTNode) : PestExpressionImpl(node), PsiLanguageInjectionHost {
	override fun isValidHost() = true
	override fun updateText(text: String) = replace(PestTokenType.createExpression(text, project)) as? PestStringMixin
	override fun createLiteralTextEscaper(): LiteralTextEscaper<PestStringMixin> = PestStringEscaper(this)
}

abstract class PestFixedBuiltinRuleNameMixin(node: ASTNode) :PestElement(node) {}
abstract class PestOverwritableRuleNameMixin(node: ASTNode) : PestElement(node) {}
abstract class PestRuleNameMixin(node: ASTNode)  :PestElement(node) {}