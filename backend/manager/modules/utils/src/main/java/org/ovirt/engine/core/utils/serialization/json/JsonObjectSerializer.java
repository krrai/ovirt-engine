/**
 *
 */
package org.ovirt.engine.core.utils.serialization.json;

import java.io.IOException;
import java.io.Serializable;

import org.apache.commons.lang.SerializationException;
import org.codehaus.jackson.JsonGenerationException;
import org.codehaus.jackson.map.JsonMappingException;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.map.SerializationConfig.Feature;
import org.ovirt.engine.core.common.action.VdcActionParametersBase;
import org.ovirt.engine.core.common.businessentities.IVdcQueryable;
import org.ovirt.engine.core.compat.Guid;
import org.ovirt.engine.core.compat.NGuid;
import org.ovirt.engine.core.utils.SerializationExeption;
import org.ovirt.engine.core.utils.Serializer;

/**
 * {@link Serializer} implementation for deserializing JSON content.
 */
public class JsonObjectSerializer implements Serializer {

    @Override
    public String serialize(Serializable payload) throws SerializationExeption {
        ObjectMapper mapper = new ObjectMapper();
        mapper.getSerializationConfig().addMixInAnnotations(NGuid.class, JsonNGuidMixIn.class);
        mapper.getSerializationConfig().addMixInAnnotations(Guid.class, JsonNGuidMixIn.class);
        mapper.getSerializationConfig().addMixInAnnotations(VdcActionParametersBase.class, JsonVdcActionParametersBaseMixIn.class);
        mapper.getSerializationConfig().addMixInAnnotations(IVdcQueryable.class, JsonIVdcQueryableMixIn.class);
        mapper.configure(Feature.INDENT_OUTPUT, true);
        mapper.enableDefaultTyping();
        return writeJsonAsString(payload, mapper);
    }

    /**
     * Use the ObjectMapper to parse the payload to String.
     *
     * @param payload
     *            - The payload to be reutrned.
     * @param mapper
     *            - The ObjectMapper.
     * @return Parsed string of the serialized object.
     */
    private String writeJsonAsString(Serializable payload, ObjectMapper mapper) {
        try {
            return mapper.writeValueAsString(payload);
        } catch (JsonGenerationException e) {
            throw new SerializationException(e);
        } catch (JsonMappingException e) {
            throw new SerializationException(e);
        } catch (IOException e) {
            throw new SerializationException(e);
        }
    }

    /**
     * Parse the serialized content with unformatted Json.
     *
     * @param payload
     *            - The serialized Object.
     * @return The string value of the serialized object.
     * @throws SerializationExeption
     */
    public String serializeUnformattedJson(Serializable payload) throws SerializationExeption {
        ObjectMapper mapper = new ObjectMapper();
        return writeJsonAsString(payload, mapper);
    }
}
