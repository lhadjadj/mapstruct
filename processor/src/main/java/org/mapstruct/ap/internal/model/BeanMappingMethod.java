/**
 *  Copyright 2012-2017 Gunnar Morling (http://www.gunnarmorling.de/)
 *  and/or other contributors as indicated by the @authors tag. See the
 *  copyright.txt file in the distribution for a full listing of all
 *  contributors.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.mapstruct.ap.internal.model;

import static org.mapstruct.ap.internal.util.Collections.first;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import javax.lang.model.type.DeclaredType;
import javax.tools.Diagnostic;

import org.mapstruct.ap.internal.model.PropertyMapping.ConstantMappingBuilder;
import org.mapstruct.ap.internal.model.PropertyMapping.JavaExpressionMappingBuilder;
import org.mapstruct.ap.internal.model.PropertyMapping.PropertyMappingBuilder;
import org.mapstruct.ap.internal.model.common.Parameter;
import org.mapstruct.ap.internal.model.common.Type;
import org.mapstruct.ap.internal.model.dependency.GraphAnalyzer;
import org.mapstruct.ap.internal.model.dependency.GraphAnalyzer.GraphAnalyzerBuilder;
import org.mapstruct.ap.internal.model.source.ForgedMethod;
import org.mapstruct.ap.internal.model.source.ForgedMethodHistory;
import org.mapstruct.ap.internal.model.source.Mapping;
import org.mapstruct.ap.internal.model.source.MappingOptions;
import org.mapstruct.ap.internal.model.source.Method;
import org.mapstruct.ap.internal.model.source.PropertyEntry;
import org.mapstruct.ap.internal.model.source.SelectionParameters;
import org.mapstruct.ap.internal.model.source.SourceMethod;
import org.mapstruct.ap.internal.model.source.SourceReference;
import org.mapstruct.ap.internal.model.source.TargetReference;
import org.mapstruct.ap.internal.prism.BeanMappingPrism;
import org.mapstruct.ap.internal.prism.CollectionMappingStrategyPrism;
import org.mapstruct.ap.internal.prism.NullValueMappingStrategyPrism;
import org.mapstruct.ap.internal.prism.ReportingPolicyPrism;
import org.mapstruct.ap.internal.util.MapperConfiguration;
import org.mapstruct.ap.internal.util.Message;
import org.mapstruct.ap.internal.util.Strings;
import org.mapstruct.ap.internal.util.accessor.Accessor;
import org.mapstruct.ap.internal.util.accessor.ExecutableElementAccessor;

/**
 * A {@link MappingMethod} implemented by a {@link Mapper} class which maps one bean type to another, optionally
 * configured by one or more {@link PropertyMapping}s.
 *
 * @author Gunnar Morling
 */
public class BeanMappingMethod extends NormalTypeMappingMethod {

    private final List<PropertyMapping> propertyMappings;
    private final Map<String, List<PropertyMapping>> mappingsByParameter;
    private final List<PropertyMapping> constantMappings;
    private final Type resultType;

    public static class Builder {

        private MappingBuilderContext ctx;
        private Method method;
        private Map<String, Accessor> unprocessedTargetProperties;
        private Set<String> targetProperties;
        private final List<PropertyMapping> propertyMappings = new ArrayList<PropertyMapping>();
        private final Set<Parameter> unprocessedSourceParameters = new HashSet<Parameter>();
        private NullValueMappingStrategyPrism nullValueMappingStrategy;
        private SelectionParameters selectionParameters;
        private final Set<String> existingVariableNames = new HashSet<String>();
        private Map<String, List<Mapping>> methodMappings;
        private SingleMappingByTargetPropertyNameFunction singleMapping;

        public Builder mappingContext(MappingBuilderContext mappingContext) {
            this.ctx = mappingContext;
            return this;
        }

        public Builder souceMethod(SourceMethod sourceMethod) {
            singleMapping = new SourceMethodSingleMapping( sourceMethod );
            return setupMethodWithMapping( sourceMethod );
        }

        public Builder forgedMethod(Method method ) {
            singleMapping = new EmptySingleMapping();
            return setupMethodWithMapping( method  );
        }

        private Builder setupMethodWithMapping(Method sourceMethod) {
            this.method = sourceMethod;
            this.methodMappings = sourceMethod.getMappingOptions().getMappings();
            CollectionMappingStrategyPrism cms = sourceMethod.getMapperConfiguration().getCollectionMappingStrategy();
            Map<String, Accessor> accessors = method.getResultType().getPropertyWriteAccessors( cms );
            this.targetProperties = accessors.keySet();

            this.unprocessedTargetProperties = new LinkedHashMap<String, Accessor>( accessors );
            for ( Parameter sourceParameter : method.getSourceParameters() ) {
                unprocessedSourceParameters.add( sourceParameter );
            }
            existingVariableNames.addAll( method.getParameterNames() );
            return this;
        }

        public Builder selectionParameters(SelectionParameters selectionParameters) {
            this.selectionParameters = selectionParameters;
            return this;
        }

        public Builder nullValueMappingStrategy(NullValueMappingStrategyPrism nullValueMappingStrategy) {
            this.nullValueMappingStrategy = nullValueMappingStrategy;
            return this;
        }

        public BeanMappingMethod build() {
            // map properties with mapping
            boolean mappingErrorOccured = handleDefinedMappings();
            if ( mappingErrorOccured ) {
                return null;
            }

            if ( !method.getMappingOptions().isRestrictToDefinedMappings() ) {

                // map properties without a mapping
                applyPropertyNameBasedMapping();

                // map parameters without a mapping
                applyParameterNameBasedMapping();
            }

            // report errors on unmapped properties
            reportErrorForUnmappedTargetPropertiesIfRequired();

            // mapNullToDefault
            boolean mapNullToDefault = method.getMapperConfiguration().isMapToDefault( nullValueMappingStrategy );


            BeanMappingPrism beanMappingPrism = BeanMappingPrism.getInstanceOn( method.getExecutable() );

            MethodReference factoryMethod = null;
            if ( !method.isUpdateMethod() ) {
                factoryMethod = ctx.getMappingResolver().getFactoryMethod(
                    method,
                    method.getResultType(),
                    selectionParameters
                );
            }

            // if there's no factory method, try the resultType in the @BeanMapping
            Type resultType = null;
            if ( factoryMethod == null ) {
                if ( selectionParameters != null && selectionParameters.getResultType() != null ) {
                    resultType = ctx.getTypeFactory().getType( selectionParameters.getResultType() );
                    if ( resultType.isAbstract() ) {
                        ctx.getMessager().printMessage(
                            method.getExecutable(),
                            beanMappingPrism.mirror,
                            Message.BEANMAPPING_ABSTRACT,
                            resultType,
                            method.getResultType()
                        );
                    }
                    else if ( !resultType.isAssignableTo( method.getResultType() ) ) {
                        ctx.getMessager().printMessage(
                            method.getExecutable(),
                            beanMappingPrism.mirror,
                            Message.BEANMAPPING_NOT_ASSIGNABLE, resultType, method.getResultType()
                        );
                    }
                }
                else if ( !method.isUpdateMethod() && method.getReturnType().isAbstract() ) {
                    ctx.getMessager().printMessage(
                        method.getExecutable(),
                        Message.GENERAL_ABSTRACT_RETURN_TYPE,
                        method.getReturnType()
                    );
                }
            }

            sortPropertyMappingsByDependencies();

            List<LifecycleCallbackMethodReference> beforeMappingMethods = LifecycleCallbackFactory.beforeMappingMethods(
                method,
                selectionParameters,
                ctx,
                existingVariableNames );
            List<LifecycleCallbackMethodReference> afterMappingMethods =
                LifecycleCallbackFactory.afterMappingMethods( method, selectionParameters, ctx, existingVariableNames );

            return new BeanMappingMethod(
                method,
                propertyMappings,
                factoryMethod,
                mapNullToDefault,
                resultType,
                beforeMappingMethods,
                afterMappingMethods
            );
        }

        /**
         * Sources the given mappings as per the dependency relationships given via {@code dependsOn()}. If a cycle is
         * detected, an error is reported.
         */
        private void sortPropertyMappingsByDependencies() {
            GraphAnalyzerBuilder graphAnalyzerBuilder = GraphAnalyzer.builder();

            for ( PropertyMapping propertyMapping : propertyMappings ) {
                graphAnalyzerBuilder.withNode( propertyMapping.getName(), propertyMapping.getDependsOn() );
            }

            final GraphAnalyzer graphAnalyzer = graphAnalyzerBuilder.build();

            if ( !graphAnalyzer.getCycles().isEmpty() ) {
                Set<String> cycles = new HashSet<String>();
                for ( List<String> cycle : graphAnalyzer.getCycles() ) {
                    cycles.add( Strings.join( cycle, " -> " ) );
                }

                ctx.getMessager().printMessage(
                    method.getExecutable(),
                    Message.BEANMAPPING_CYCLE_BETWEEN_PROPERTIES, Strings.join( cycles, ", " )
                );
            }
            else {
                Collections.sort(
                    propertyMappings, new Comparator<PropertyMapping>() {
                        @Override
                        public int compare(PropertyMapping o1, PropertyMapping o2) {
                            return graphAnalyzer.getTraversalSequence( o1.getName() )
                                - graphAnalyzer.getTraversalSequence( o2.getName() );
                        }
                    }
                );
            }
        }

        /**
         * Iterates over all defined mapping methods ({@code @Mapping(s)}), either directly given or inherited from the
         * inverse mapping method.
         * <p>
         * If a match is found between a defined source (constant, expression, ignore or source) the mapping is removed
         * from the remaining target properties.
         * <p>
         * It is furthermore checked whether the given mappings are correct. When an error occurs, the method continues
         * in search of more problems.
         */
        private boolean handleDefinedMappings() {

            boolean errorOccurred = false;
            Set<String> handledTargets = new HashSet<String>();

            if ( method.getMappingOptions().hasNestedTargetReferences() ) {
                handleDefinedNestedTargetMapping( handledTargets );
            }

            for ( Map.Entry<String, List<Mapping>> entry : methodMappings.entrySet() ) {
                for ( Mapping mapping : entry.getValue() ) {
                    TargetReference targetReference = mapping.getTargetReference();
                    if ( targetReference.isValid() ) {
                        if ( !handledTargets.contains( first( targetReference.getPropertyEntries() ).getFullName() ) ) {
                            if ( handleDefinedMapping( mapping, handledTargets ) ) {
                                errorOccurred = true;
                            }
                        }
                    }
                    else if ( reportErrorOnTargetObject( mapping ) ) {
                        errorOccurred = true;
                    }
                }
            }
            for ( String handledTarget : handledTargets ) {
                // In order to avoid: "Unknown property foo in return type" in case of duplicate
                // target mappings
                unprocessedTargetProperties.remove( handledTarget );
            }

            return errorOccurred;
        }

        private void handleDefinedNestedTargetMapping(Set<String> handledTargets) {

            Map<PropertyEntry, MappingOptions> optionsByNestedTarget =
                method.getMappingOptions().groupByPoppedTargetReferences();
            for ( Entry<PropertyEntry, MappingOptions> entryByTP : optionsByNestedTarget.entrySet() ) {

                Map<Parameter, MappingOptions> optionsBySourceParam = entryByTP.getValue().groupBySourceParameter();
                for ( Entry<Parameter, MappingOptions> entryByParam : optionsBySourceParam.entrySet() ) {

                    SourceReference sourceRef = new SourceReference.BuilderFromProperty()
                        .sourceParameter( entryByParam.getKey() )
                        .name( entryByTP.getKey().getName() )
                        .build();

                    PropertyMapping propertyMapping = new PropertyMappingBuilder()
                        .mappingContext( ctx )
                        .sourceMethod( method )
                        .targetProperty( entryByTP.getKey() )
                        .targetPropertyName( entryByTP.getKey().getName() )
                        .sourceReference( sourceRef )
                        .existingVariableNames( existingVariableNames )
                        .dependsOn( entryByParam.getValue().collectNestedDependsOn() )
                        .forgeMethodWithMappingOptions( entryByParam.getValue() )
                        .build();
                    unprocessedSourceParameters.remove( sourceRef.getParameter() );

                    if ( propertyMapping != null ) {
                        propertyMappings.add( propertyMapping );
                    }
                }
                handledTargets.add( entryByTP.getKey().getName() );
            }

        }

        private boolean handleDefinedMapping(Mapping mapping, Set<String> handledTargets) {

            boolean errorOccured = false;

            PropertyMapping propertyMapping = null;

            TargetReference targetRef = mapping.getTargetReference();
            PropertyEntry targetProperty = first( targetRef.getPropertyEntries() );

            // unknown properties given via dependsOn()?
            for ( String dependency : mapping.getDependsOn() ) {
                if ( !targetProperties.contains( dependency ) ) {
                    ctx.getMessager().printMessage(
                        method.getExecutable(),
                        mapping.getMirror(),
                        mapping.getDependsOnAnnotationValue(),
                        Message.BEANMAPPING_UNKNOWN_PROPERTY_IN_DEPENDS_ON,
                        dependency
                    );
                    errorOccured = true;
                }
            }

            // check the mapping options
            // its an ignored property mapping
            if ( mapping.isIgnored() ) {
                propertyMapping = null;
                handledTargets.add( mapping.getTargetName() );
            }

            // its a plain-old property mapping
            else if ( mapping.getSourceName() != null ) {

                // determine source parameter
                SourceReference sourceRef = mapping.getSourceReference();
                if ( sourceRef.isValid() ) {

                    // targetProperty == null can occur: we arrived here because we want as many errors
                    // as possible before we stop analysing
                    propertyMapping = new PropertyMappingBuilder()
                        .mappingContext( ctx )
                        .sourceMethod( method )
                        .targetProperty( targetProperty )
                        .targetPropertyName( mapping.getTargetName() )
                        .sourceReference( sourceRef )
                        .selectionParameters( mapping.getSelectionParameters() )
                        .formattingParameters( mapping.getFormattingParameters() )
                        .existingVariableNames( existingVariableNames )
                        .dependsOn( mapping.getDependsOn() )
                        .defaultValue( mapping.getDefaultValue() )
                        .build();
                    handledTargets.add( targetProperty.getName() );
                    unprocessedSourceParameters.remove( sourceRef.getParameter() );
                }
                else {
                   errorOccured = true;
                }
            }

            // its a constant
            else if ( mapping.getConstant() != null ) {

                propertyMapping = new ConstantMappingBuilder()
                    .mappingContext( ctx )
                    .sourceMethod( method )
                    .constantExpression( "\"" + mapping.getConstant() + "\"" )
                    .targetProperty( targetProperty )
                    .targetPropertyName( mapping.getTargetName() )
                    .formattingParameters( mapping.getFormattingParameters() )
                    .selectionParameters( mapping.getSelectionParameters() )
                    .existingVariableNames( existingVariableNames )
                    .dependsOn( mapping.getDependsOn() )
                    .build();
                handledTargets.add( mapping.getTargetName() );
            }

            // its an expression
            else if ( mapping.getJavaExpression() != null ) {

                propertyMapping = new JavaExpressionMappingBuilder()
                    .mappingContext( ctx )
                    .sourceMethod( method )
                    .javaExpression( mapping.getJavaExpression() )
                    .existingVariableNames( existingVariableNames )
                    .targetProperty( targetProperty )
                    .targetPropertyName( mapping.getTargetName() )
                    .dependsOn( mapping.getDependsOn() )
                    .build();
                handledTargets.add( mapping.getTargetName() );
            }

            // remaining are the mappings without a 'source' so, 'only' a date format or qualifiers
            if ( propertyMapping != null ) {
                propertyMappings.add( propertyMapping );
            }

            return errorOccured;
        }

        private boolean reportErrorOnTargetObject(Mapping mapping) {

            boolean errorOccurred = false;

            boolean hasReadAccessor
                = method.getResultType().getPropertyReadAccessors().containsKey( mapping.getTargetName() );

            if ( hasReadAccessor ) {
                if ( !mapping.isIgnored() ) {
                    ctx.getMessager().printMessage(
                        method.getExecutable(),
                        mapping.getMirror(),
                        mapping.getSourceAnnotationValue(),
                        Message.BEANMAPPING_PROPERTY_HAS_NO_WRITE_ACCESSOR_IN_RESULTTYPE,
                        mapping.getTargetName() );
                    errorOccurred = true;
                }
            }
            else {
                ctx.getMessager().printMessage(
                    method.getExecutable(),
                    mapping.getMirror(),
                    mapping.getSourceAnnotationValue(),
                    Message.BEANMAPPING_UNKNOWN_PROPERTY_IN_RESULTTYPE,
                    mapping.getTargetName() );
                errorOccurred = true;
            }
            return errorOccurred;
        }

        /**
         * Iterates over all target properties and all source parameters.
         * <p>
         * When a property name match occurs, the remainder will be checked for duplicates. Matches will be removed from
         * the set of remaining target properties.
         */
        private void applyPropertyNameBasedMapping() {

            Iterator<Entry<String, Accessor>> targetPropertyEntriesIterator =
                unprocessedTargetProperties.entrySet().iterator();

            while ( targetPropertyEntriesIterator.hasNext() ) {

                Entry<String, Accessor> targetProperty = targetPropertyEntriesIterator.next();
                String targetPropertyName = targetProperty.getKey();

                PropertyMapping propertyMapping = null;

                if ( propertyMapping == null ) {

                    for ( Parameter sourceParameter : method.getSourceParameters() ) {

                        Type sourceType = sourceParameter.getType();

                        if ( sourceType.isPrimitive() ) {
                            continue;
                        }

                        PropertyMapping newPropertyMapping = null;

                        Accessor sourceReadAccessor =
                            sourceParameter.getType().getPropertyReadAccessors().get( targetPropertyName );

                        ExecutableElementAccessor sourcePresenceChecker =
                            sourceParameter.getType().getPropertyPresenceCheckers().get( targetPropertyName );

                        if ( sourceReadAccessor != null ) {
                            Mapping mapping = singleMapping.getSingleMappingByTargetPropertyName(
                                targetProperty.getKey() );
                            DeclaredType declaredSourceType = (DeclaredType) sourceParameter.getType().getTypeMirror();

                            SourceReference sourceRef = new SourceReference.BuilderFromProperty()
                                .sourceParameter( sourceParameter )
                                .type( ctx.getTypeFactory().getReturnType( declaredSourceType, sourceReadAccessor ) )
                                .readAccessor( sourceReadAccessor )
                                .presenceChecker( sourcePresenceChecker )
                                .name( targetProperty.getKey() )
                                .build();

                            newPropertyMapping = new PropertyMappingBuilder()
                                .mappingContext( ctx )
                                .sourceMethod( method )
                                .targetWriteAccessor( targetProperty.getValue() )
                                .targetReadAccessor( getTargetPropertyReadAccessor( targetPropertyName ) )
                                .targetPropertyName( targetPropertyName )
                                .sourceReference( sourceRef )
                                .formattingParameters( mapping != null ? mapping.getFormattingParameters() : null )
                                .selectionParameters( mapping != null ? mapping.getSelectionParameters() : null )
                                .defaultValue( mapping != null ? mapping.getDefaultValue() : null )
                                .existingVariableNames( existingVariableNames )
                                .dependsOn( mapping != null ? mapping.getDependsOn() : Collections.<String>emptyList() )
                                .build();

                            unprocessedSourceParameters.remove( sourceParameter );
                        }

                        if ( propertyMapping != null && newPropertyMapping != null ) {
                            // TODO improve error message
                            ctx.getMessager().printMessage(
                                method.getExecutable(),
                                Message.BEANMAPPING_SEVERAL_POSSIBLE_SOURCES,
                                targetPropertyName
                            );
                            break;
                        }
                        else if ( newPropertyMapping != null ) {
                            propertyMapping = newPropertyMapping;
                        }
                    }
                }

                if ( propertyMapping != null ) {
                    propertyMappings.add( propertyMapping );
                    targetPropertyEntriesIterator.remove();
                }
            }
        }

        private void applyParameterNameBasedMapping() {

            Iterator<Entry<String, Accessor>> targetPropertyEntriesIterator =
                unprocessedTargetProperties.entrySet().iterator();

            while ( targetPropertyEntriesIterator.hasNext() ) {

                Entry<String, Accessor> targetProperty = targetPropertyEntriesIterator.next();

                Iterator<Parameter> sourceParameters = unprocessedSourceParameters.iterator();

                while ( sourceParameters.hasNext() ) {

                    Parameter sourceParameter = sourceParameters.next();
                    if ( sourceParameter.getName().equals( targetProperty.getKey() ) ) {
                        Mapping mapping = singleMapping.getSingleMappingByTargetPropertyName( targetProperty.getKey() );

                        SourceReference sourceRef = new SourceReference.BuilderFromProperty()
                            .sourceParameter( sourceParameter )
                            .name( targetProperty.getKey() )
                            .build();

                        PropertyMapping propertyMapping = new PropertyMappingBuilder()
                            .mappingContext( ctx )
                            .sourceMethod( method )
                            .targetWriteAccessor( targetProperty.getValue() )
                            .targetReadAccessor( getTargetPropertyReadAccessor( targetProperty.getKey() ) )
                            .targetPropertyName( targetProperty.getKey() )
                            .sourceReference( sourceRef )
                            .formattingParameters( mapping != null ? mapping.getFormattingParameters() : null )
                            .selectionParameters( mapping != null ? mapping.getSelectionParameters() : null )
                            .existingVariableNames( existingVariableNames )
                            .dependsOn( mapping != null ? mapping.getDependsOn() : Collections.<String>emptyList() )
                            .build();

                        propertyMappings.add( propertyMapping );
                        targetPropertyEntriesIterator.remove();
                        sourceParameters.remove();
                    }
                }
            }
        }

        private Accessor getTargetPropertyReadAccessor(String propertyName) {
            return method.getResultType().getPropertyReadAccessors().get( propertyName );
        }

        private ReportingPolicyPrism getUnmappedTargetPolicy() {
            MapperConfiguration mapperSettings = MapperConfiguration.getInstanceOn( ctx.getMapperTypeElement() );

            return mapperSettings.unmappedTargetPolicy( ctx.getOptions() );
        }

        private void reportErrorForUnmappedTargetPropertiesIfRequired() {

            // fetch settings from element to implement
            ReportingPolicyPrism unmappedTargetPolicy = getUnmappedTargetPolicy();

            //we handle forged methods differently than the usual source ones. in
            if ( method instanceof ForgedMethod ) {

                ForgedMethod forgedMethod = (ForgedMethod) this.method;
                if ( forgedMethod.isAutoMapping()
                    && ( targetProperties.isEmpty() || !unprocessedTargetProperties.isEmpty() ) ) {

                    if ( forgedMethod.getHistory() == null ) {
                        Type sourceType = this.method.getParameters().get( 0 ).getType();
                        Type targetType = this.method.getReturnType();
                        ctx.getMessager().printMessage(
                            this.method.getExecutable(),
                            Message.PROPERTYMAPPING_FORGED_MAPPING_NOT_FOUND,
                            sourceType,
                            targetType,
                            targetType,
                            sourceType
                        );
                    }
                    else {
                        ForgedMethodHistory history = forgedMethod.getHistory();
                        ctx.getMessager().printMessage(
                            this.method.getExecutable(),
                            Message.PROPERTYMAPPING_MAPPING_NOT_FOUND,
                            history.createSourcePropertyErrorMessage(),
                            history.getTargetType(),
                            history.createTargetPropertyName(),
                            history.getTargetType(),
                            history.getSourceType()
                        );
                    }

                }
            }
            else if ( !unprocessedTargetProperties.isEmpty() && unmappedTargetPolicy.requiresReport() ) {

                Message msg = unmappedTargetPolicy.getDiagnosticKind() == Diagnostic.Kind.ERROR ?
                    Message.BEANMAPPING_UNMAPPED_TARGETS_ERROR : Message.BEANMAPPING_UNMAPPED_TARGETS_WARNING;

                ctx.getMessager().printMessage(
                    method.getExecutable(),
                    msg,
                    MessageFormat.format(
                        "{0,choice,1#property|1<properties}: \"{1}\"",
                        unprocessedTargetProperties.size(),
                        Strings.join( unprocessedTargetProperties.keySet(), ", " )
                    )
                );
            }
        }
    }

    private BeanMappingMethod(Method method,
                              List<PropertyMapping> propertyMappings,
                              MethodReference factoryMethod,
                              boolean mapNullToDefault,
                              Type resultType,
                              List<LifecycleCallbackMethodReference> beforeMappingReferences,
                              List<LifecycleCallbackMethodReference> afterMappingReferences) {
        super(
            method,
            factoryMethod,
            mapNullToDefault,
            beforeMappingReferences,
            afterMappingReferences
        );

        this.propertyMappings = propertyMappings;

        // intialize constant mappings as all mappings, but take out the ones that can be contributed to a
        // parameter mapping.
        this.mappingsByParameter = new HashMap<String, List<PropertyMapping>>();
        this.constantMappings = new ArrayList<PropertyMapping>( propertyMappings );
        for ( Parameter sourceParameter : getSourceParameters() ) {
            ArrayList<PropertyMapping> mappingsOfParameter = new ArrayList<PropertyMapping>();
            mappingsByParameter.put( sourceParameter.getName(), mappingsOfParameter );
            for ( PropertyMapping mapping : propertyMappings ) {
                if ( sourceParameter.getName().equals( mapping.getSourceBeanName() ) ) {
                    mappingsOfParameter.add( mapping );
                    constantMappings.remove( mapping );
                }
            }
        }
        this.resultType = resultType;
    }

    public List<PropertyMapping> getPropertyMappings() {
        return propertyMappings;
    }

    public List<PropertyMapping> getConstantMappings() {
        return constantMappings;
    }

    public Map<String, List<PropertyMapping>> getPropertyMappingsByParameter() {
        return mappingsByParameter;
    }

    @Override
    public Type getResultType() {
        if ( resultType == null ) {
            return super.getResultType();
        }
        else {
            return resultType;
        }
    }

    @Override
    public Set<Type> getImportTypes() {
        Set<Type> types = super.getImportTypes();

        for ( PropertyMapping propertyMapping : propertyMappings ) {
            types.addAll( propertyMapping.getImportTypes() );
        }

        return types;
    }

    public List<Parameter> getSourceParametersExcludingPrimitives() {
        List<Parameter> sourceParameters = new ArrayList<Parameter>();
        for ( Parameter sourceParam : getSourceParameters() ) {
            if ( !sourceParam.getType().isPrimitive() ) {
                sourceParameters.add( sourceParam );
            }
        }

        return sourceParameters;
    }

    public List<Parameter> getSourcePrimitiveParameters() {
        List<Parameter> sourceParameters = new ArrayList<Parameter>();
        for ( Parameter sourceParam : getSourceParameters() ) {
            if ( sourceParam.getType().isPrimitive() ) {
                sourceParameters.add( sourceParam );
            }
        }
        return sourceParameters;
    }

    @Override
    public int hashCode() {
        //Needed for Checkstyle, otherwise it fails due to EqualsHashCode rule
        return super.hashCode();
    }

    @Override
    public boolean equals(Object obj) {

        if ( this == obj ) {
            return true;
        }
        if ( obj == null || getClass() != obj.getClass() ) {
            return false;
        }

        BeanMappingMethod that = (BeanMappingMethod) obj;

        if ( !super.equals( obj ) ) {
            return false;
        }
        return propertyMappings != null ? propertyMappings.equals( that.propertyMappings ) :
            that.propertyMappings == null;
    }

    private interface SingleMappingByTargetPropertyNameFunction {

        Mapping getSingleMappingByTargetPropertyName(String targetPropertyName);
    }

    private static class EmptySingleMapping implements SingleMappingByTargetPropertyNameFunction {

        @Override
        public Mapping getSingleMappingByTargetPropertyName(String targetPropertyName) {
            return null;
        }
    }

    private static class SourceMethodSingleMapping implements SingleMappingByTargetPropertyNameFunction {

        private final SourceMethod sourceMethod;

        private SourceMethodSingleMapping(SourceMethod sourceMethod) {
            this.sourceMethod = sourceMethod;
        }

        @Override
        public Mapping getSingleMappingByTargetPropertyName(String targetPropertyName) {
            return sourceMethod.getSingleMappingByTargetPropertyName( targetPropertyName );
        }
    }

}

