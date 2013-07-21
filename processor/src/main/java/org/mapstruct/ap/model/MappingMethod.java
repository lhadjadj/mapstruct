/**
 *  Copyright 2012-2013 Gunnar Morling (http://www.gunnarmorling.de/)
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
package org.mapstruct.ap.model;

import java.beans.Introspector;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.mapstruct.ap.model.source.Method;
import org.mapstruct.ap.model.source.Parameter;
import org.mapstruct.ap.util.Strings;

/**
 * A method implemented or referenced by a {@link Mapper} class.
 *
 * @author Gunnar Morling
 */
public abstract class MappingMethod extends AbstractModelElement {

    private final String name;
    private final List<Parameter> parameters;
    private final Type returnType;
    private final Parameter singleSourceParameter;
    private final Parameter targetParameter;

    public MappingMethod(Method method) {
        this.name = method.getName();
        this.parameters = method.getParameters();
        this.returnType = method.getReturnType();
        this.singleSourceParameter = method.getSingleSourceParameter();
        this.targetParameter = method.getTargetParameter();
    }

    public String getName() {
        return name;
    }

    public List<Parameter> getParameters() {
        return parameters;
    }

    public Parameter getSingleSourceParameter() {
        return singleSourceParameter;
    }

    public Type getResultType() {
        return targetParameter != null ? targetParameter.getType() : returnType;
    }

    public String getResultName() {
        return targetParameter != null ? targetParameter.getName() :
            Strings.getSaveVariableName( Introspector.decapitalize( getResultType().getName() ), getParameterNames() );
    }

    public Type getReturnType() {
        return returnType;
    }

    public boolean isExistingInstanceMapping() {
        return targetParameter != null;
    }

    @Override
    public Set<Type> getImportTypes() {
        Set<Type> types = new HashSet<Type>();

        for ( Parameter param : parameters ) {
            types.add( param.getType() );
        }

        types.add( getReturnType() );

        return types;
    }

    protected List<String> getParameterNames() {
        List<String> parameterNames = new ArrayList<String>( parameters.size() );

        for ( Parameter parameter : parameters ) {
            parameterNames.add( parameter.getName() );
        }

        return parameterNames;
    }

    @Override
    public String toString() {
        return returnType + " " + getName() + "(" + Strings.join( parameters, ", " ) + ")";
    }
}
