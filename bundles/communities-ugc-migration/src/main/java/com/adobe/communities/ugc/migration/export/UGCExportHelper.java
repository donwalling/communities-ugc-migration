/*************************************************************************
 *
 * ADOBE SYSTEMS INCORPORATED
 * Copyright 2015 Adobe Systems Incorporated
 * All Rights Reserved.
 *
 * NOTICE:  Adobe permits you to use, modify, and distribute this file in accordance with the
 * terms of the Adobe license agreement accompanying it.  If you have received this file from a
 * source other than Adobe, then your use, modification, or distribution of it requires the prior
 * written permission of Adobe.
 **************************************************************************/
package com.adobe.communities.ugc.migration.export;

import java.io.IOException;
import java.io.InputStream;
import java.io.Writer;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.Map;

import com.adobe.communities.ugc.migration.ContentTypeDefinitions;
import org.apache.commons.codec.binary.Base64;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ValueMap;
import org.apache.sling.commons.json.JSONArray;
import org.apache.sling.commons.json.JSONException;
import org.apache.sling.commons.json.io.JSONWriter;

public class UGCExportHelper {

    private final static int DATA_ENCODING_CHUNK_SIZE = 1440;

    public static void extractSubNode(JSONWriter object, final Resource node) throws JSONException {
        final ValueMap childVm = node.getValueMap();
        final JSONArray timestampFields = new JSONArray();
        for (Map.Entry<String, Object> prop : childVm.entrySet()) {
            final Object value = prop.getValue();
            if (value instanceof String[]) {
                final JSONArray list = new JSONArray();
                for (String v : (String[]) value) {
                    list.put(v);
                }
                object.key(prop.getKey());
                object.value(list);
            } else if (value instanceof GregorianCalendar) {
                timestampFields.put(prop.getKey());
                object.key(prop.getKey());
                object.value(((Calendar) value).getTimeInMillis());
            } else if (value instanceof InputStream) {
                object.key(ContentTypeDefinitions.LABEL_ENCODED_DATA_FIELDNAME);
                object.value(prop.getKey());
                object.key(ContentTypeDefinitions.LABEL_ENCODED_DATA);
                object.value(""); //if we error out on the first read attempt, we need a placeholder value still
                try {
                    final InputStream data = (InputStream) value;
                    byte[] byteData = new byte[DATA_ENCODING_CHUNK_SIZE];
                    int read = 0;
                    while (read != -1) {
                        read = data.read(byteData);
                        if (read > 0 && read < DATA_ENCODING_CHUNK_SIZE) {
                            // make a right-size container for the byte data actually read
                            byte[] byteArray = new byte[read];
                            System.arraycopy(byteData, 0, byteArray, 0, read);
                            byte[] encodedBytes = Base64.encodeBase64(byteArray);
                            object.value(new String(encodedBytes));
                        } else if (read == DATA_ENCODING_CHUNK_SIZE) {
                            byte[] encodedBytes = Base64.encodeBase64(byteData);
                            object.value(new String(encodedBytes));
                        }
                    }
                } catch (IOException e) {
                    object.key(ContentTypeDefinitions.LABEL_ERROR);
                    object.value("IOException while getting attachment: " + e.getMessage());
                }
            } else {
                object.key(prop.getKey());
                object.value(prop.getValue());
            }
        }
        if (timestampFields.length() > 0) {
            object.key(ContentTypeDefinitions.LABEL_TIMESTAMP_FIELDS);
            object.value(timestampFields);
        }
        if (node.hasChildren()) {
            object.key(ContentTypeDefinitions.LABEL_SUBNODES);
            object.object();
            for (final Resource subNode : node.getChildren()) {
                object.key(subNode.getName());
                JSONWriter subObject = object.object();
                extractSubNode(subObject, subNode);
                object.endObject();
            }
            object.endObject();
        }
    }
    public static void extractAttachment(final Writer ioWriter, final JSONWriter writer, final Resource node)
            throws JSONException {
        Resource contentNode = node.getChild("jcr:content");
        if (contentNode == null) {
            writer.key(ContentTypeDefinitions.LABEL_ERROR);
            writer.value("provided resource was not an attachment - no content node");
            return;
        }
        ValueMap content = contentNode.getValueMap();
        if (!content.containsKey("jcr:mimeType") || !content.containsKey("jcr:data")) {
            writer.key(ContentTypeDefinitions.LABEL_ERROR);
            writer.value("provided resource was not an attachment - content node contained no attachment data");
            return;
        }
        writer.key("filename");
        writer.value(node.getName());
        writer.key("jcr:mimeType");
        writer.value(content.get("jcr:mimeType"));

        try {
            ioWriter.write(",\"jcr:data\":\"");
            final InputStream data = (InputStream) content.get("jcr:data");
            byte[] byteData = new byte[DATA_ENCODING_CHUNK_SIZE];
            int read = 0;
            while (read != -1) {
                read = data.read(byteData);
                if (read > 0 && read < DATA_ENCODING_CHUNK_SIZE) {
                    // make a right-size container for the byte data actually read
                    byte[] byteArray = new byte[read];
                    System.arraycopy(byteData, 0, byteArray, 0, read);
                    byte[] encodedBytes = Base64.encodeBase64(byteArray);
                    ioWriter.write(new String(encodedBytes));
                } else if (read == DATA_ENCODING_CHUNK_SIZE) {
                    byte[] encodedBytes = Base64.encodeBase64(byteData);
                    ioWriter.write(new String(encodedBytes));
                }
            }
            ioWriter.write("\"");
        } catch (IOException e) {
            writer.key(ContentTypeDefinitions.LABEL_ERROR);
            writer.value("IOException while getting attachment: " + e.getMessage());
        }
    }
}



