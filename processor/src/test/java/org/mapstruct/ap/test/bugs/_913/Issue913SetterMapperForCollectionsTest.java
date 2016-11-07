/**
 *  Copyright 2012-2016 Gunnar Morling (http://www.gunnarmorling.de/)
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
package org.mapstruct.ap.test.bugs._913;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mapstruct.ap.testutil.IssueKey;
import org.mapstruct.ap.testutil.WithClasses;
import org.mapstruct.ap.testutil.runner.AnnotationProcessorTestRunner;

/**
 * All these test cases test the possible combinations in the SetterMapperForCollections.
 *
 * The target object is assumed to have getter and setter access.
 *
 * @author Sjaak Derksen
 */
@RunWith(AnnotationProcessorTestRunner.class)
@WithClasses({
    Domain.class,
    Dto.class,
    DtoWithPresenceCheck.class,
    DomainDtoWithNvmsNullMapper.class,
    DomainDtoWithNvmsDefaultMapper.class,
    DomainDtoWithPresenceCheckMapper.class,
    Helper.class})
@IssueKey( "913" )
public class Issue913SetterMapperForCollectionsTest {

     /**
     * The null value mapping strategy on type level (Mapper) should generate forged methods for the
     * conversion from string to long that return null in the entire mapper, so also for the forged
     * mapper. Note the default NVMS is RETURN_NULL.
     */
    @Test
    public void shouldReturnNullForNvmsReturnNullForCreate() {

        Dto dto = new Dto();
        Domain domain = DomainDtoWithNvmsNullMapper.INSTANCE.create( dto );

        doControlAsserts( domain );
        assertThat( domain.getStrings() ).isNull();
        assertThat( domain.getLongs() ).isNull();
    }

    /**
     * The null value mapping strategy on type level (Mapper) should generate forged methods for the
     * conversion from string to long that return null in the entire mapper, so also for the forged
     * mapper. Note the default NVMS is RETURN_NULL.
     */
    @Test
    public void shouldReturnNullForNvmsReturnNullForUpdate() {

        Dto dto = new Dto();
        Domain domain = new Domain();
        DomainDtoWithNvmsNullMapper.INSTANCE.update( dto, domain );

        doControlAsserts( domain );
        assertThat( domain.getStrings() ).isNull();
        assertThat( domain.getLongs() ).isNull();

    }

    /**
     * The null value mapping strategy on type level (Mapper) should generate forged methods for the
     * conversion from string to long that return null in the entire mapper, so also for the forged
     * mapper. Note the default NVMS is RETURN_NULL.
     *
     * target (stringsInitialized is Not Null) and source (stringInitialized is Null) target should
     * be explicitely set to null
     */
    @Test
    public void shouldReturnNullForNvmsReturnNullForUpdateWithNonNullTargetAndNullSource() {

        Dto dto = new Dto();
        dto.setStringsInitialized( null );
        Domain domain = new Domain();
        DomainDtoWithNvmsNullMapper.INSTANCE.update( dto, domain );

        assertThat( domain.getStringsInitialized() ).isNull();
        assertThat( domain.getLongsInitialized() ).isNull();
        assertThat( domain.getStringsWithDefault() ).containsOnly( "3" );
        assertThat( domain.getStrings() ).isNull();
        assertThat( domain.getLongs() ).isNull();

    }

    /**
     * The null value mapping strategy on type level (Mapper) should generate forged methods for the
     * conversion from string to long that return null in the entire mapper, so also for the forged
     * mapper. Note the default NVMS is RETURN_NULL.
     */
    @Test
    public void shouldReturnNullForNvmsReturnNullForUpdateWithReturn() {

        Dto dto = new Dto();
        Domain domain1 = new Domain();
        Domain domain2 = DomainDtoWithNvmsNullMapper.INSTANCE.updateWithReturn( dto, domain1 );

        doControlAsserts( domain1, domain2 );
        assertThat( domain1.getStrings() ).isNull();
        assertThat( domain1.getLongs() ).isNull();
        assertThat( domain2.getStrings() ).isNull();
        assertThat( domain2.getLongs() ).isNull();
    }

   /**
     * The null value mapping strategy on type level (Mapper) should generate forged methods for the
     * conversion from string to long that return default in the entire mapper, so also for the forged
     * mapper. Note the default NVMS is RETURN_NULL.
     *
     * However, for plain mappings (strings to strings) the result will be null.
     */
    @Test
    public void shouldReturnDefaultForNvmsReturnDefaultForCreate() {

        Dto dto = new Dto();
        Domain domain = DomainDtoWithNvmsDefaultMapper.INSTANCE.create( dto );

        doControlAsserts( domain );
        assertThat( domain.getStrings() ).isNull();
        assertThat( domain.getLongs() ).isEmpty();
    }

    /**
     * The null value mapping strategy on type level (Mapper) should generate forged methods for the conversion from
     * string to long that return default in the entire mapper, so also for the forged mapper. Note the default NVMS is
     * RETURN_NULL.
     *
     * However, for plain mappings (strings to strings) the result will be null.
     */
    @Test
    public void shouldReturnDefaultForNvmsReturnDefaultForUpdate() {

        Dto dto = new Dto();
        Domain domain = new Domain();
        DomainDtoWithNvmsDefaultMapper.INSTANCE.update( dto, domain );

        doControlAsserts( domain );
        assertThat( domain.getStrings() ).isNull();
        assertThat( domain.getLongs() ).isEmpty();
    }

    /**
     * The null value mapping strategy on type level (Mapper) should generate forged methods for the
     * conversion from string to long that return default in the entire mapper, so also for the forged
     * mapper. Note the default NVMS is
     * RETURN_NULL.
     *
     * However, for plain mappings (strings to strings) the result will be null.
     *
     */
    @Test
    public void shouldReturnDefaultForNvmsReturnDefaultForUpdateWithReturn() {

        Dto dto = new Dto();
        Domain domain1 = new Domain();
        Domain domain2 = DomainDtoWithNvmsDefaultMapper.INSTANCE.updateWithReturn( dto, domain1 );

        doControlAsserts( domain1, domain2 );
        assertThat( domain1.getLongs() ).isEqualTo( domain2.getLongs() );
        assertThat( domain1.getStrings() ).isNull();
        assertThat( domain1.getLongs() ).isEmpty();
        assertThat( domain2.getStrings() ).isNull();
        assertThat( domain2.getLongs() ).isEmpty();
    }

    /**
     * Test create method ICW presence checker
     *
     */
    @Test
    public void shouldReturnNullForCreateWithPresenceChecker() {

        DtoWithPresenceCheck dto = new DtoWithPresenceCheck();
        Domain domain = DomainDtoWithPresenceCheckMapper.INSTANCE.create( dto );

        doControlAsserts( domain );
        assertThat( domain.getStrings() ).isNull();
        assertThat( domain.getLongs() ).isNull();
    }

    /**
     * Test update method ICW presence checker
     *
     */
    @Test
    public void shouldReturnNullForUpdateWithPresenceChecker() {

        DtoWithPresenceCheck dto = new DtoWithPresenceCheck();
        Domain domain = new Domain();
        DomainDtoWithPresenceCheckMapper.INSTANCE.update( dto, domain );

        doControlAsserts( domain );
        assertThat( domain.getStrings() ).isNull();
        assertThat( domain.getLongs() ).isNull();
    }

    /**
     * Test update with return method ICW presence checker
     *
     */
    @Test
    public void shouldReturnNullForUpdateWithReturnWithPresenceChecker() {

        DtoWithPresenceCheck dto = new DtoWithPresenceCheck();
        Domain domain1 = new Domain();
        Domain domain2 = DomainDtoWithPresenceCheckMapper.INSTANCE.updateWithReturn( dto, domain1 );

        doControlAsserts( domain1, domain2 );
        assertThat( domain1.getLongs() ).isEqualTo( domain2.getLongs() );
        assertThat( domain1.getStrings() ).isNull();
        assertThat( domain1.getLongs() ).isNull();
        assertThat( domain2.getStrings() ).isNull();
        assertThat( domain2.getLongs() ).isNull();
    }



    /**
     * These assert check if non-null and default mapping is working as expected.
     *
     * @param domain
     */
    private void doControlAsserts( Domain domain ) {
        assertThat( domain.getStringsInitialized() ).containsOnly( "5" );
        assertThat( domain.getLongsInitialized() ).containsOnly( 5L );
        assertThat( domain.getStringsWithDefault() ).containsOnly( "3" );
    }

    /**
     * These assert check if non-null and default mapping is working as expected.
     *
     * @param domain
     */
    private void doControlAsserts( Domain domain1, Domain domain2) {
        assertThat( domain1 ).isEqualTo( domain2 );
        assertThat( domain1.getStringsInitialized() ).containsOnly( "5" );
        assertThat( domain1.getLongsInitialized() ).containsOnly( 5L );
        assertThat( domain1.getStringsWithDefault() ).containsOnly( "3" );
        assertThat( domain2.getStringsInitialized() ).containsOnly( "5" );
        assertThat( domain2.getLongsInitialized() ).containsOnly( 5L );
        assertThat( domain2.getStringsWithDefault() ).containsOnly( "3" );
        assertThat( domain1.getStringsInitialized() ).isEqualTo( domain2.getStringsInitialized() );
        assertThat( domain1.getLongsInitialized() ).isEqualTo( domain2.getLongsInitialized() );
        assertThat( domain1.getStringsWithDefault() ).isEqualTo( domain2.getStringsWithDefault() );
    }

}