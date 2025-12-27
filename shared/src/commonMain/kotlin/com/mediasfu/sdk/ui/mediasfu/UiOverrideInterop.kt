package com.mediasfu.sdk.ui.mediasfu

import androidx.compose.runtime.Composable

/**
 * Compose equivalent of Flutter's DefaultComponentBuilder signature.
 */
typealias DefaultComponentBuilder<T> = @Composable (T) -> Unit

/**
 * Allows overrides to either fully replace a component or wrap the default implementation.
 */
data class ComponentOverride<T>(
    val component: DefaultComponentBuilder<T>? = null,
    val render: (@Composable (T, DefaultComponentBuilder<T>) -> Unit)? = null,
)

/**
 * Mirrors the Flutter FunctionOverride helper so logic hooks remain parity-friendly.
 */
data class FunctionOverride<T : Function<*>>(
    val implementation: T? = null,
    val wrap: ((T) -> T)? = null,
)

/**
 * Applies a component override, returning a builder that respects replacement and wrapper semantics.
 */
fun <T> withOverride(
    override: ComponentOverride<T>?,
    baseBuilder: DefaultComponentBuilder<T>,
): DefaultComponentBuilder<T> {
    override?.component?.let { return it }

    override?.render?.let { render ->
        return { options -> render(options, baseBuilder) }
    }

    return baseBuilder
}

/**
 * Applies a function override, mirroring the Flutter helper behavior.
 */
@Suppress("UNCHECKED_CAST")
fun <T : Function<*>> withFunctionOverride(
    base: T,
    override: FunctionOverride<T>?,
): T {
    override?.implementation?.let { return it }
    override?.wrap?.let { wrapper -> return wrapper(base) }
    return base
}
