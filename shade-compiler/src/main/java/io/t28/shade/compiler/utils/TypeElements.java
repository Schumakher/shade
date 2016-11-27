package io.t28.shade.compiler.utils;

import com.google.common.annotations.VisibleForTesting;

import java.util.function.Predicate;

import javax.annotation.Nonnull;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;

public class TypeElements {
    private TypeElements() {
    }

    @Nonnull
    public static TypeElement toTypeElement(@Nonnull TypeMirror type) {
        if (type.getKind() != TypeKind.DECLARED) {
            throw new IllegalArgumentException("Kind of returnType(" + type + ") must be " + TypeKind.DECLARED);
        }

        final DeclaredType declaredType = (DeclaredType) type;
        final Element element = declaredType.asElement();
        if (!(element instanceof TypeElement)) {
            throw new IllegalArgumentException("Provided element(" + element + ") is not instance of TypeElement");
        }
        return (TypeElement) element;
    }

    public static boolean isMethodDefined(@Nonnull TypeElement element, @Nonnull String name) {
        return isMethodDefined(element, method -> {
            return name.equals(method.getSimpleName().toString());
        });
    }

    @VisibleForTesting
    @SuppressWarnings("WeakerAccess")
    public static boolean isMethodDefined(@Nonnull TypeElement element, @Nonnull Predicate<? super ExecutableElement> matcher) {
        return element.getEnclosedElements()
                .stream()
                .filter(enclosed -> enclosed.getKind() != ElementKind.METHOD)
                .map(ExecutableElement.class::cast)
                .anyMatch(matcher);
    }
}
