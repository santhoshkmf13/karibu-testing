@file:Suppress("FunctionName")

package com.github.mvysny.kaributesting.v10

import com.vaadin.flow.component.*
import com.vaadin.flow.component.button.Button
import com.vaadin.flow.component.combobox.ComboBox
import com.vaadin.flow.component.datepicker.DatePicker
import com.vaadin.flow.component.html.Input
import com.vaadin.flow.component.textfield.PasswordField
import com.vaadin.flow.component.textfield.TextArea
import com.vaadin.flow.component.textfield.TextField
import com.vaadin.flow.dom.Element
import com.vaadin.flow.dom.ElementUtil
import com.vaadin.flow.router.HasErrorParameter
import com.vaadin.flow.router.Route
import com.vaadin.flow.router.RouterLink
import com.vaadin.flow.server.startup.RouteRegistry
import io.github.classgraph.ClassGraph
import io.github.classgraph.ScanResult
import org.atmosphere.util.annotation.AnnotationDetector
import org.jsoup.nodes.Document
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode
import java.io.ByteArrayOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.io.Serializable
import kotlin.test.expect

fun Serializable.serializeToBytes(): ByteArray = ByteArrayOutputStream().use { it -> ObjectOutputStream(it).writeObject(this); it }.toByteArray()
inline fun <reified T: Serializable> ByteArray.deserialize(): T = ObjectInputStream(inputStream()).readObject() as T
inline fun <reified T: Serializable> T.serializeDeserialize() = serializeToBytes().deserialize<T>()

/**
 * A configuration object of all routes and error routes in the application. Use [autoDiscoverViews] to discover everything.
 */
class Routes: Serializable {
    val routes = mutableSetOf<Class<out Component>>()
    val errorRoutes = mutableSetOf<Class<out HasErrorParameter<*>>>()

    /**
     * Manually register error routes. No longer needed since [autoDiscoverViews] can now detect error routes.
     */
    @Deprecated("No longer needed, error routes are now auto-detected with autoDiscoverViews()", ReplaceWith(""))
    fun addErrorRoutes(vararg routes: Class<out HasErrorParameter<*>>): Routes = apply {
        errorRoutes.addAll(routes.toSet())
    }

    /**
     * Creates a Vaadin 10 registry from this configuration object.
     */
    fun createRegistry() : RouteRegistry = object : RouteRegistry() {
        init {
            setNavigationTargets(routes)
            setErrorNavigationTargets(errorRoutes.map { it.asSubclass(Component::class.java) } .toMutableSet())
        }
    }

    /**
     * Auto-discovers everything, registers it into `this` and returns `this`.
     * * [Route]-annotated views
     * * [HasErrorParameter] error views
     * @param packageName set the package name for the detector to be faster; or provide null to scan the whole classpath, but this is quite slow.
     * @param autoDetectErrorRoutes if false then [HasErrorParameter] error views are not auto-detected. This emulates
     * the old behavior of this method.
     */
    @JvmOverloads
    fun autoDiscoverViews(packageName: String? = null, autoDetectErrorRoutes: Boolean = true): Routes = apply {
        val scan: ScanResult = ClassGraph().enableClassInfo()
                .enableAnnotationInfo()
                .whitelistPackages(*(if (packageName == null) arrayOf() else arrayOf(packageName))).scan()
        scan.use { scanResult ->
            scanResult.getClassesWithAnnotation(Route::class.java.name).mapTo(routes) { info ->
                Class.forName(info.name).asSubclass(Component::class.java)
            }
            if (autoDetectErrorRoutes) {
                scanResult.getClassesImplementing(HasErrorParameter::class.java.name).mapTo(errorRoutes) { info ->
                    Class.forName(info.name).asSubclass(HasErrorParameter::class.java)
                }
            }
        }

        println("Auto-discovered views: ${routes.joinToString { it.simpleName }}")
        println("Auto-discovered error routes: ${errorRoutes.joinToString { it.simpleName }}")
    }
}

/**
 * Allows us to fire any Vaadin event on any Vaadin component.
 * @receiver the component, not null.
 * @param event the event, not null.
 */
fun Component._fireEvent(event: ComponentEvent<*>) {
    ComponentUtil.fireEvent(this, event)
}

/**
 * Determines the component's `label` (it's the HTML element's `label` property actually). Intended to be used for fields such as [TextField].
 */
var Component.label: String
    get() = element.getProperty("label") ?: ""
    set(value) {
        element.setProperty("label", if (value.isBlank()) null else value)
    }

/**
 * The Component's caption: [Button.getText] for [Button], [label] for fields such as [TextField].
 */
var Component.caption: String
    get() = when (this) {
        is Button -> text
        else -> label
    }
    set(value) {
        when (this) {
            is Button -> text = value
            else -> label = value
        }
    }
/**
 * Workaround for https://github.com/vaadin/flow/issues/664
 */
var Component.id_: String?
    get() = id.orElse(null)
    set(value) { setId(value) }

val Component.isAttached: Boolean
    get() = ui.orElse(null)?.session != null

val IntRange.size: Int get() = (endInclusive + 1 - start).coerceAtLeast(0)

val Component._isVisible: Boolean get() = when (this) {
    is Text -> !text.isNullOrBlank()   // workaround for https://github.com/vaadin/flow/issues/3201
    else -> isVisible
}

/**
 * Returns direct text contents (it doesn't peek into the child elements).
 */
val Component._text: String? get() = when (this) {
    is HasText -> text
    is Text -> text   // workaround for https://github.com/vaadin/flow/issues/3606
    else -> null
}

/**
 * Clicks the button, but only if it is actually possible to do so by the user. If the button is read-only or disabled, it throws an exception.
 * @throws IllegalArgumentException if the button was not visible, not enabled, read-only or if no button (or too many buttons) matched.
 */
fun Button._click() {
    checkEditableByUser()
    // click()  // can't call this since this calls JS method on the browser... but we're server-testing and there is no browser and this call would do nothing.
    _fireEvent(ClickEvent<Button>(this))
}

private fun Component.checkEditableByUser() {
    check(isEffectivelyVisible()) { "The ${toPrettyString()} is not effectively visible - either it is hidden, or its ascendant is hidden" }
    val parentNullOrEnabled = !parent.isPresent || parent.get().isEffectivelyEnabled()
    if (parentNullOrEnabled) {
        check(isEnabled) { "The ${toPrettyString()} is not enabled" }
    }
    check(isEffectivelyEnabled()) { "The ${toPrettyString()} is nested in a disabled component" }
    if (this is HasValue<*, *>) {
        @Suppress("UNCHECKED_CAST")
        val hasValue = this as HasValue<HasValue.ValueChangeEvent<Any?>, Any?>
        check(!hasValue.isReadOnly) { "The ${toPrettyString()} is read-only" }
    }
}

private fun Component.isEffectivelyVisible(): Boolean = isVisible && (!parent.isPresent || parent.get().isEffectivelyVisible())

/**
 * This function actually works, as opposed to [Element.getTextRecursively].
 */
val Element.textRecursively2: String get() {
    // remove when this is fixed: https://github.com/vaadin/flow/issues/3668
    val node = ElementUtil.toJsoup(Document(""), this)
    return node.textRecursively
}

val Node.textRecursively: String get() = when (this) {
    is TextNode -> this.text()
    else -> childNodes().joinToString(separator = "", transform = { it.textRecursively })
}

/**
 * Computes that this component and all of its parents are enabled.
 * @return false if this component or any of its parent is disabled.
 */
fun Component.isEffectivelyEnabled(): Boolean = isEnabled && (!parent.isPresent || parent.get().isEffectivelyEnabled())

/**
 * Checks whether this component is [HasEnabled.isEnabled]. All components not implementing [HasEnabled] are considered enabled.
 */
val Component.isEnabled: Boolean get() = when (this) {
    is HasEnabled -> isEnabled
    else -> true
}

/**
 * Sets the value of given component, but only if it is actually possible to do so by the user.
 * If the component is read-only or disabled, an exception is thrown.
 * @throws IllegalStateException if the field was not visible, not enabled or was read-only.
 */
var <V, E: HasValue.ValueChangeEvent<V>> HasValue<E, V>._value: V?
    get() = value
    set(v) {
        (this as Component).checkEditableByUser()
        value = v
    }

// modify when this is fixed: https://github.com/vaadin/flow/issues/4068
val Component.placeholder: String?
    get() = when (this) {
        is TextField -> placeholder
        is TextArea -> placeholder
        is PasswordField -> placeholder
        is ComboBox<*> -> this.placeholder  // https://youtrack.jetbrains.com/issue/KT-24275
        is DatePicker -> placeholder
        is Input -> placeholder.orElse(null)
        else -> null
    }

/**
 * Navigates to where this router link points to.
 */
fun RouterLink.click() {
    UI.getCurrent().navigate(href)
}

/**
 * Expects that [actual] list of objects matches [expected] list of objects. Fails otherwise.
 */
fun <T> expectList(vararg expected: T, actual: ()->List<T>) = expect(expected.toList(), actual)

/**
 * Removes the component from its parent. Does nothing if the component does not have a parent.
 */
fun Component.removeFromParent() {
    (parent.orElse(null) as? HasComponents)?.remove(this)
}
