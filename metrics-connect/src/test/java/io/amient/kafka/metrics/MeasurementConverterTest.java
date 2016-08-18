package io.amient.kafka.metrics;

import org.apache.kafka.connect.data.SchemaAndValue;
import org.junit.Test;

import java.util.HashMap;

import static org.junit.Assert.assertEquals;


public class MeasurementConverterTest {

    @Test
    public void endToEndConversionTest() {
        InternalAvroSerde internalAvro = new InternalAvroSerde();
        MeasurementFormatter formatter = new MeasurementFormatter();
        MeasurementConverter converter = new MeasurementConverter();
        converter.configure(new HashMap<String, Object>(), false);

        MeasurementV1.Builder builder = MeasurementV1.newBuilder();
        builder.setTimestamp(System.currentTimeMillis());
        builder.setName("xyz.abc.123");
        builder.setTags(new HashMap<String, String>(){{
            put("dimension1", "tag1");
            put("dimension2", "tag2");
        }});
        builder.setFields(new HashMap<String, Double>(){{
            put("Value1", 10.0);
            put("Value2", 0.0);
        }});
        MeasurementV1 m = builder.build();
        System.out.println(formatter.toString(m));

        SchemaAndValue schemaAndValue = converter.toConnectData("topic1", internalAvro.toBytes(m));

        MeasurementV1 m2 = internalAvro.fromBytes(converter.fromConnectData("topic1", schemaAndValue.schema(), schemaAndValue.value()));

        System.out.println(formatter.toString(m2));

        assertEquals(m, m2);
    }
}
