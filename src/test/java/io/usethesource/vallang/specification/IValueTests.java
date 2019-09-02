package io.usethesource.vallang.specification;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.StringReader;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ArgumentsSource;

import io.usethesource.vallang.GivenValue;
import io.usethesource.vallang.IMapWriter;
import io.usethesource.vallang.INode;
import io.usethesource.vallang.IValue;
import io.usethesource.vallang.IValueFactory;
import io.usethesource.vallang.NoAnnotations;
import io.usethesource.vallang.ValueProvider;
import io.usethesource.vallang.exceptions.FactTypeUseException;
import io.usethesource.vallang.io.StandardTextReader;
import io.usethesource.vallang.io.StandardTextWriter;

public class IValueTests {
    
    @ParameterizedTest @ArgumentsSource(ValueProvider.class) 
    public void equalsIsReflexive(@NoAnnotations IValue val) {
        assertEquals(val, val);
    }
    
    @ParameterizedTest @ArgumentsSource(ValueProvider.class)  
    public void equalsIsCommutative(@NoAnnotations IValue val1, @NoAnnotations IValue val2) {
        assertTrue(!val1.equals(val2) || val2.equals(val1));
    }
    
    @ParameterizedTest @ArgumentsSource(ValueProvider.class) 
    public void equalsIsTransitive(@NoAnnotations IValue val1, @NoAnnotations IValue val2, @NoAnnotations IValue val3) {
        assertTrue(!(val1.equals(val2) && val2.equals(val3)) || val1.equals(val3));
    }
    
    @ParameterizedTest @ArgumentsSource(ValueProvider.class)
    public void testHashCodeContract(IValue val1, IValue val2) {
        if (val1.equals(val2)) {
            assertEquals(val1.hashCode(), val2.hashCode(), "" + val1.toString() + " and " + val2.toString() + " are equal but do not have the same hashCode?");
        }
        assertTrue(!val1.equals(val2) || val1.hashCode() == val2.hashCode());
    }
    
    @ParameterizedTest @ArgumentsSource(ValueProvider.class) 
    public void testWysiwyg(IValueFactory vf, @NoAnnotations IValue val) throws FactTypeUseException, IOException {
        StandardTextReader reader = new StandardTextReader();
        String string = val.toString();
        IValue result = reader.read(vf, val.getType(), new StringReader(string));
        assertEquals(val, result, "reading back " + val + " produced something different");
    }

    @SuppressWarnings("deprecation")
    @ParameterizedTest @ArgumentsSource(ValueProvider.class)
    public void bug39Repo(IValueFactory vf) throws IOException {
        INode val = vf.node("59", vf.bool(false), vf.integer(-6));

        IMapWriter mapForAnno = vf.mapWriter();
        mapForAnno.put(vf.datetime(6404, 3, 11, 9, 37, 6, 202, 0, 0),
                vf.tuple(vf.string(""), vf.string("")));
        mapForAnno.put(vf.datetime(2020, 10,26, 18, 36, 56, 342, 0,0),
                vf.tuple(vf.string("kc"), vf.string("햿ŏŤD")));
        mapForAnno.put(vf.datetime(374,2,28, 13, 59, 16, 535, 0,0),
                vf.tuple(vf.string(""), vf.string("")));
        mapForAnno.put(vf.datetime(5254,11,30, 22, 54, 53, 946, 0, 0),
                vf.tuple(vf.string(""), vf.string("f792")));

        val  = val.asAnnotatable().setAnnotation("FgG1217", mapForAnno.done());
        val = val.asAnnotatable().setAnnotation("JhI4449", vf.list(
                vf.datetime(2020, 5, 31, 23, 30, 19, 184, 0,0),
                vf.datetime(2020, 3, 24, 1,33, 1, 663, 0, 0)));
        val = val.asAnnotatable().setAnnotation("vRf1459", vf.bool(false));
        val = val.asAnnotatable().setAnnotation("Okrg81h", vf.rational(1193539202, 2144242729));

        testWysiwyg(vf, val);
    }

    @ParameterizedTest @ArgumentsSource(ValueProvider.class) 
    public void testIsomorphicText(@NoAnnotations IValue val1, @NoAnnotations IValue val2) throws FactTypeUseException, IOException {
        // (val1 == val2) <==> (val1.toString() == val2.toString())

        if (val1.equals(val2)) {
            assertEquals(val1.toString(), val2.toString(), val1.toString() + " and " + val2.toString() + " should look the same because they are equal.");
        }
        
        if (val1.toString().equals(val2.toString())) {
            assertEquals(val1, val2, val1.toString() + " and " + val2.toString() + "should be equal because they look the same.");
        }
    }
    
    @ParameterizedTest @ArgumentsSource(ValueProvider.class) 
    public void testIsomorphicText_regression_48(
            @GivenValue("\"(|Da:///7w/gSfqB/Y/avO7N/06/gf96/0/52/5498/622h/4/8KTb/%C2%A9%C2%A3/LCdq|:\\\"y\\\"(4.875329280939582,false,$2020-02-19T01:25:19.036+00:00$),|cj://W03|:\\\"YPE\\\"(),|IZwo:///vcV|:\\\"df\\\"(\\\"iRe\\\"()[@zynZaJw=-404415087088.54184696206636425,@aeI6931=[],@BTlDgfs=0.0],0),|lcJr:///rmG7?C=dqen|:\\\"AHZQ\\\"((),{-1721001437r1218015923})[@Wta4336=true,@TLGcrBZ=false,@UwzdGnJ=[false],@DBnDip4=true],|l:///94/4|:\\\"aRH\\\"(1333314580r109079989))\"") IValue val1, 
            @GivenValue("\"(|Da:///7w/gSfqB/Y/avO7N/06/gf96/0/52/5498/622h/4/8KTb/%C2%A9%C2%A3/LCdq|:\\\"y\\\"(4.875329280939582,false,$2020-02-19T01:25:19.036+00:00$),|cj://W03|:\\\"YPE\\\"(),|IZwo:///vcV|:\\\"df\\\"(\\\"iRe\\\"()[@zynZaJw=-404415087088.54184696206636425,@aeI6931=[],@BTlDgfs=0.0],0),|lcJr:///rmG7?C=dqen|:\\\"AHZQ\\\"((),{-1721001437r1218015923})[@Wta4336=true,@TLGcrBZ=false,@UwzdGnJ=[false],@DBnDip4=true],|l:///94/4|:\\\"aRH\\\"(1333314580r109079989))\"") IValue val2) throws FactTypeUseException, IOException {
        testIsomorphicText(val1, val2);
    }
    
    @ParameterizedTest @ArgumentsSource(ValueProvider.class)
    public void testWysiwygAnnos(IValueFactory vf, IValue val) throws FactTypeUseException, IOException {
        StandardTextReader reader = new StandardTextReader();
        String string = val.toString();
        IValue result = reader.read(vf, val.getType(), new StringReader(string));
        assertTrue(val.isEqual(result), val.toString() + " is not read back properly."); // isEqual ignores annotations
    }
    
    @ParameterizedTest @ArgumentsSource(ValueProvider.class)
    public void testToStringIsStandardardTextWriter(IValueFactory vf, IValue val) throws FactTypeUseException, IOException {
        assertEquals(val.toString(), StandardTextWriter.valueToString(val), "toString of " + val + " is not equal to the standard notation");
    }
    
    @ParameterizedTest @ArgumentsSource(ValueProvider.class)
    public void testNoValueInstancesShouldEverHaveFieldNamesInTheirDynamicTypes(IValue val) {
        assertFalse(val.getType().hasFieldNames());
    }
}
