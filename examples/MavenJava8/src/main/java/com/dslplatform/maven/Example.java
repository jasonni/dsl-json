package com.dslplatform.maven;

import com.dslplatform.json.*;
import com.dslplatform.json.runtime.Settings;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.*;

public class Example {

	@CompiledJson(onUnknown = CompiledJson.Behavior.IGNORE) //ignore unknown properties (default for objects). to disallow unknown properties in JSON set it to FAIL which will result in exception instead
	public static class Model {
		@JsonAttribute(nullable = false) //indicate that field can't be null
		public String string;
		public List<Integer> integers;
		@JsonAttribute(name = "guids") //use alternative name in JSON
		public UUID[] uuids;
		public Set<BigDecimal> decimals;
		public Vector<Long> longs;
		@JsonAttribute(hashMatch = false) // exact name match can be forced, otherwise hash value will be used for matching
		public int number;
		@JsonAttribute(alternativeNames = {"old_nested", "old_nested2"}) //several JSON attribute names can be deserialized into this field
		public List<Nested> nested;
		@JsonAttribute(typeSignature = CompiledJson.TypeSignature.EXCLUDE) //$type attribute can be excluded from resulting JSON
		public Abstract abs;//abstract classes or interfaces can be used which will also include $type attribute in JSON by default
		public List<Abstract> absList;
		public Interface iface;//interfaces without deserializedAs will also include $type attribute in JSON by default
		public ParentClass inheritance;
		@JsonAttribute(mandatory = true) // currently up to 64 mandatory properties can be used per bean
		public List<State> states;
		public JsonObjectReference jsonObject; //object implementing JsonObject manage their own conversion. They must start with '{'
		public List<JsonObjectReference> jsonObjects;
		@JsonAttribute(ignore = true)
		public GregorianCalendar ignored;
		public LocalTime time; //LocalTime is not supported, but with the use of converter it will work
		public List<LocalTime> times; //even containers with unsupported type will be resolved
		@JsonAttribute(converter = FormatDecimal2.class)
		public BigDecimal decimal2; //custom formatting can be implemented with per property converters
		public ArrayList<Integer> intList; //most collections are supported through runtime converters
		public Map<String, Object> map; //even unknown stuff can be used. If it fails it will throw SerializationException
		public ImmutablePerson person; //immutable objects are supported via builder pattern

		//explicitly referenced classes don't require @CompiledJson annotation
		public static class Nested {
			public long x;
			public double y;
			public float z;
		}

		@CompiledJson(deserializeAs = Concrete.class)//without deserializeAs deserializing Abstract would fails since it doesn't contain a $type due to it's exclusion in the above configuration
		public static abstract class Abstract {
			public int x;
		}

		//since this class is not explicitly referenced, but it's an extension of the abstract class used as a property
		//it needs to be decorated with annotation
		@CompiledJson
		public static class Concrete extends Abstract {
			public long y;
		}

		public interface Interface {
			void x(int v);
			int x();
		}

		@CompiledJson(deserializeName = "custom-name")//by default class name will be used for $type attribute
		public static class WithCustomCtor implements Interface {
			private int x;
			private int y;

			public WithCustomCtor(int x) {
				this.x = x;
				this.y = x;
			}

			@CompiledJson
			public WithCustomCtor(int x, int y) {
				this.x = x;
				this.y = y;
			}

			public void x(int v) { x = v; }
			public int x() { return x; }
			public void setY(int v) { y = v; }
			public int getY() { return y; }
		}

		public static class BaseClass {
			public int a;
		}

		public static class ParentClass extends BaseClass {
			public long b;
		}

		public enum State {
			LOW(0),
			MID(1),
			HI(2);

			private final int value;

			State(int value) {
				this.value = value;
			}
		}

		public static class JsonObjectReference implements JsonObject {

			public final int x;
			public final String s;

			public JsonObjectReference(int x, String s) {
				this.x = x;
				this.s = s;
			}

			public void serialize(JsonWriter writer, boolean minimal) {
				writer.writeAscii("{\"x\":");
				NumberConverter.serialize(x, writer);
				writer.writeAscii(",\"s\":");
				StringConverter.serialize(s, writer);
				writer.writeAscii("}");
			}

			public static final JsonReader.ReadJsonObject<JsonObjectReference> JSON_READER = new JsonReader.ReadJsonObject<JsonObjectReference>() {
				public JsonObjectReference deserialize(JsonReader reader) throws IOException {
					reader.fillName();//"x"
					reader.getNextToken();//start number
					int x = NumberConverter.deserializeInt(reader);
					reader.getNextToken();//,
					reader.getNextToken();//start name
					reader.fillName();//"s"
					reader.getNextToken();//start string
					String s = StringConverter.deserialize(reader);
					reader.getNextToken();//}
					return new JsonObjectReference(x, s);
				}
			};
		}
		@JsonConverter(target = LocalTime.class)
		public static abstract class LocalTimeConverter {
			public static final JsonReader.ReadObject<LocalTime> JSON_READER = reader -> {
				if (reader.wasNull()) return null;
				return LocalTime.parse(reader.readSimpleString());
			};
			public static final JsonWriter.WriteObject<LocalTime> JSON_WRITER = (writer, value) -> {
				if (value == null) {
					writer.writeNull();
				} else {
					writer.writeString(value.toString());
				}
			};
		}
		public static abstract class FormatDecimal2 {
			public static final JsonReader.ReadObject<BigDecimal> JSON_READER = reader -> {
				if (reader.wasNull()) return null;
				return NumberConverter.deserializeDecimal(reader).setScale(2);
			};
			public static final JsonWriter.WriteObject<BigDecimal> JSON_WRITER = (writer, value) -> {
				if (value == null) {
					writer.writeNull();
				} else {
					NumberConverter.serializeNullable(value.setScale(2), writer);
				}
			};
		}
	}

	public static void main(String[] args) throws IOException {

		//ServiceLoader.load will load Model since it will be registered into META-INF/services during annotation processing
		//withRuntime is enabled to support runtime analysis for stuff which is not registered by default
		//Annotation processor will run by default and generate descriptions for JSON encoding/decoding
		DslJson<Object> dslJson = new DslJson<>(Settings.withRuntime().allowArrayFormat(true).includeServiceLoader());
		//writer should be reused. For per thread reuse use ThreadLocal pattern
		JsonWriter writer = dslJson.newWriter();

		Model instance = new Model();
		instance.string = "Hello World!";
		instance.number = 42;
		instance.integers = Arrays.asList(1, 2, 3);
		instance.decimals = new HashSet<>(Arrays.asList(BigDecimal.ONE, BigDecimal.ZERO));
		instance.uuids = new UUID[]{new UUID(1L, 2L), new UUID(3L, 4L)};
		instance.longs = new Vector<>(Arrays.asList(1L, 2L));
		instance.nested = Arrays.asList(new Model.Nested(), null);
		instance.inheritance = new Model.ParentClass();
		instance.inheritance.a = 5;
		instance.inheritance.b = 6;
		instance.iface = new Model.WithCustomCtor(5, 6);
		instance.person = new ImmutablePerson("first name", "last name", 35);
		instance.states = Arrays.asList(Model.State.HI, Model.State.LOW);
		instance.jsonObject = new Model.JsonObjectReference(43, "abcd");
		instance.jsonObjects = Collections.singletonList(new Model.JsonObjectReference(34, "dcba"));
		instance.time = LocalTime.of(12, 15);
		instance.times = Arrays.asList(null, LocalTime.of(8, 16));
		Model.Concrete concrete = new Model.Concrete();
		concrete.x = 11;
		concrete.y = 23;
		instance.abs = concrete;
		instance.absList = Arrays.<Model.Abstract>asList(concrete, null, concrete);
		instance.decimal2 = BigDecimal.TEN;
		instance.intList = new ArrayList<>(Arrays.asList(123, 456));
		instance.map = new HashMap<>();
		instance.map.put("abc", 678);
		instance.map.put("array", new int[] { 2, 4, 8});

		dslJson.serialize(writer, instance);

		//resulting buffer with JSON
		byte[] buffer = writer.getByteBuffer();
		//end of buffer
		int size = writer.size();

		//deserialization using byte[] API
		Model deser = dslJson.deserialize(Model.class, buffer, size);

		System.out.println(deser.string);
	}
}
