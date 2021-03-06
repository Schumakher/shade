/*
 * Copyright (c) 2016 Tatsuya Maki
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.t28.shade.processor.factory;

import android.annotation.SuppressLint;
import android.support.annotation.NonNull;

import com.google.common.base.CaseFormat;
import com.google.common.collect.ImmutableList;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterSpec;
import com.squareup.javapoet.TypeName;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import javax.annotation.Nonnull;
import javax.inject.Inject;
import javax.inject.Named;
import javax.lang.model.element.Modifier;
import javax.lang.model.type.TypeMirror;

import io.t28.shade.internal.EqualsBuilder;
import io.t28.shade.internal.HashCodeBuilder;
import io.t28.shade.internal.ToStringBuilder;
import io.t28.shade.processor.metadata.PreferenceClassMetadata;
import io.t28.shade.processor.metadata.PropertyMethodMetadata;
import io.t28.shade.processor.util.CodeBlocks;

import static java.util.stream.Collectors.toList;

@SuppressLint("NewApi")
public class ModelClassFactory extends TypeFactory {
    private static final String METHOD_NAME_EQUALS = "equals";
    private static final String METHOD_NAME_HASH_CODE = "hashCode";
    private static final String METHOD_NAME_TO_STRING = "toString";

    private final PreferenceClassMetadata preference;
    private final List<PropertyMethodMetadata> properties;
    private final ClassName modelClass;
    private final ClassName modelImplClass;

    @Inject
    public ModelClassFactory(@Nonnull PreferenceClassMetadata preference,
                             @Nonnull @Named("Model") ClassName modelClass,
                             @Nonnull @Named("ModelImpl") ClassName modelImplClass) {

        this.preference = preference;
        this.properties = preference.getPropertyMethods();
        this.modelClass = modelClass;
        this.modelImplClass = modelImplClass;
    }

    @Nonnull
    @Override
    protected String getName() {
        return modelImplClass.simpleName();
    }

    @Nonnull
    @Override
    protected List<Modifier> getModifiers() {
        return ImmutableList.of(Modifier.PUBLIC, Modifier.STATIC);
    }

    @Nonnull
    @Override
    protected Optional<TypeName> getSuperClass() {
        if (preference.isClass()) {
            return Optional.of(modelClass);
        }
        return Optional.empty();
    }

    @Nonnull
    @Override
    protected List<TypeName> getInterfaces() {
        if (preference.isInterface()) {
            return Collections.singletonList(modelClass);
        }
        return Collections.emptyList();
    }

    @Nonnull
    @Override
    protected List<FieldSpec> getFields() {
        return ImmutableList.copyOf(properties.stream()
                .map(property -> {
                    final String fieldName = property.getSimpleNameWithoutPrefix(CaseFormat.LOWER_CAMEL);
                    final TypeName valueType = property.getReturnTypeName();
                    return FieldSpec.builder(valueType, fieldName, Modifier.PRIVATE, Modifier.FINAL).build();
                })
                .collect(toList()));
    }

    @Nonnull
    @Override
    protected List<MethodSpec> getMethods() {
        return ImmutableList.<MethodSpec>builder()
                .add(buildConstructorSpec())
                .add(buildEqualsMethodSpec())
                .add(buildHashCodeMethodSpec())
                .add(buildToStringMethodSpec())
                .addAll(buildGetMethodSpecs())
                .build();
    }

    private MethodSpec buildConstructorSpec() {
        final MethodSpec.Builder builder = MethodSpec.constructorBuilder();
        builder.addModifiers(Modifier.PUBLIC);

        // Parameters
        properties.forEach(property -> {
            final TypeName valueType = property.getReturnTypeName();
            final String fieldName = property.getSimpleNameWithoutPrefix(CaseFormat.LOWER_CAMEL);
            final ParameterSpec parameter;
            if (valueType.isPrimitive()) {
                parameter = ParameterSpec.builder(valueType, fieldName).build();
            } else {
                parameter = ParameterSpec.builder(valueType, fieldName).addAnnotation(NonNull.class).build();
            }
            builder.addParameter(parameter);
        });

        // Statements
        properties.forEach(property -> {
            final TypeMirror valueType = property.getReturnType();
            final String fieldName = property.getSimpleNameWithoutPrefix(CaseFormat.LOWER_CAMEL);
            builder.addStatement("$L", CodeBlock.builder()
                    .add("this.$L = $L", fieldName, CodeBlocks.createUnmodifiableStatement(valueType, fieldName))
                    .build());
        });
        return builder.build();
    }

    private MethodSpec buildToStringMethodSpec() {
        final MethodSpec.Builder builder = MethodSpec.methodBuilder(METHOD_NAME_TO_STRING)
                .addAnnotation(NonNull.class)
                .addAnnotation(Override.class)
                .addModifiers(Modifier.PUBLIC)
                .returns(String.class);

        builder.addStatement("final $1T builder = new $1T(this)", ToStringBuilder.class);
        properties.forEach(property -> {
            final String fieldName = property.getSimpleNameWithoutPrefix(CaseFormat.LOWER_CAMEL);
            builder.addStatement("builder.append($1S, $1L)", fieldName);
        });
        builder.addStatement("return builder.toString()");
        return builder.build();
    }

    private MethodSpec buildEqualsMethodSpec() {
        final MethodSpec.Builder builder = MethodSpec.methodBuilder(METHOD_NAME_EQUALS)
                .addAnnotation(Override.class)
                .addModifiers(Modifier.PUBLIC)
                .returns(boolean.class)
                .addParameter(Object.class, "object");

        builder.beginControlFlow("if (this == object)")
                .addStatement("return true")
                .endControlFlow();

        builder.beginControlFlow("if (!(object instanceof $T))", modelClass)
                .addStatement("return false")
                .endControlFlow();

        builder.addStatement("final $T that = ($T) object", modelClass, modelClass);

        builder.addStatement("final $1T builder = new $1T()", EqualsBuilder.class);
        properties.forEach(property -> {
            final String fieldName = property.getSimpleNameWithoutPrefix(CaseFormat.LOWER_CAMEL);
            final String methodName = property.getSimpleName();
            builder.addStatement("builder.append($L, that.$L())", fieldName, methodName);
        });
        builder.addStatement("return builder.build()");
        return builder.build();
    }

    private MethodSpec buildHashCodeMethodSpec() {
        final MethodSpec.Builder builder = MethodSpec.methodBuilder(METHOD_NAME_HASH_CODE)
                .addAnnotation(Override.class)
                .addModifiers(Modifier.PUBLIC)
                .returns(int.class);

        builder.addStatement("final $1T builder = new $1T()", HashCodeBuilder.class);
        properties.forEach(property -> {
            final String fieldName = property.getSimpleNameWithoutPrefix(CaseFormat.LOWER_CAMEL);
            builder.addStatement("builder.append($L)", fieldName);
        });
        builder.addStatement("return builder.build()");
        return builder.build();
    }

    private List<MethodSpec> buildGetMethodSpecs() {
        return properties.stream()
                .map(property -> {
                    final String fieldName = property.getSimpleNameWithoutPrefix(CaseFormat.LOWER_CAMEL);
                    final TypeMirror valueType = property.getReturnType();
                    final CodeBlock statement = CodeBlocks.createUnmodifiableStatement(valueType, fieldName);
                    return MethodSpec.overriding(property.getMethod())
                            .addStatement("return $L", statement)
                            .build();
                })
                .collect(toList());
    }
}
