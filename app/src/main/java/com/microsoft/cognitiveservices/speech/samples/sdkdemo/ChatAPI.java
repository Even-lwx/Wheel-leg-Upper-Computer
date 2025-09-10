package com.microsoft.cognitiveservices.speech.samples.sdkdemo;
import android.util.Log;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.lang.reflect.Method;
import java.util.Iterator;
import java.util.Map;
public class ChatAPI {

    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static List<JsonNode> messages = new ArrayList<>();

    public static String getChatDeployment() {
        return "";
    }

    public static JsonNode getLLMResponse(List<JsonNode> messages, List<JsonNode> tools) throws IOException {
        int i = 10;
        List<JsonNode> messagesAi = messages.subList(Math.max(messages.size() - i, 0), messages.size());

        while (messagesAi.get(0).has("role") && "tool".equals(messagesAi.get(0).get("role").asText())) {
            i++;
            messagesAi = messages.subList(Math.max(messages.size() - i, 0), messages.size());
        }

        ObjectNode requestBody = objectMapper.createObjectNode();
        ArrayNode messagesArray = objectMapper.createArrayNode();
        messagesArray.addAll(messagesAi);
        //Log.i("ChatAPI","messages: "+messagesArray);
        requestBody.put("model", "deepseek-chat");
        requestBody.set("messages", messagesArray);
        requestBody.put("temperature", 0.6);
        requestBody.put("max_tokens", 500);
        //requestBody.set("tools", objectMapper.valueToTree(tools));
        //requestBody.put("tool_choice", "auto");
        requestBody.put("stream", false);

        URL url = new URL("https://api.deepseek.com/chat/completions"); // Replace with actual API endpoint
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("POST");
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setRequestProperty("Authorization", "Bearer sk-14aea57216d942e98ff2b105ba3aca65"); // Replace with actual API key
        connection.setDoOutput(true);

        try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(connection.getOutputStream()))) {
            writer.write(objectMapper.writeValueAsString(requestBody));
            writer.flush();
            //Log.i("ChatAPI","Write");
        }

        StringBuilder responseBuilder = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                responseBuilder.append(line);
                //Log.i("ChatAPI","response: "+line);
            }
        }

        return objectMapper.readTree(responseBuilder.toString()).get("choices").get(0).get("message");
    }

    public static JsonNode runConversation(List<JsonNode> messages, List<JsonNode> tools) throws IOException {
        JsonNode responseMessage = getLLMResponse(messages, tools);

        if (responseMessage.has("tool_calls")) {
            ArrayNode toolCalls = (ArrayNode) responseMessage.get("tool_calls");

            //messages.add(responseMessage);

            for (JsonNode toolCall : toolCalls) {
                System.out.println("⏳Call internal function...");
                String functionName = toolCall.get("function").get("name").asText();
                System.out.println("⏳Call " + functionName + "...");

                //Map<String, Object> functionArgs = objectMapper.convertValue(toolCall.get("function").get("arguments"), Map.class);
                ObjectMapper objectMapper = new ObjectMapper();
                JsonNode functionArgs = toolCall.get("function").get("arguments");

                System.out.println("⏳Call params: " + functionArgs);

                Object functionResponse = callFunction(functionName, functionArgs);
                System.out.println("⏳Call internal function done!");
                System.out.println("执行结果：");
                System.out.println(functionResponse);
                System.out.println("===================================");

                ObjectNode functionResponseNode = objectMapper.createObjectNode();
                functionResponseNode.put("tool_call_id", toolCall.get("id").asText());
                functionResponseNode.put("role", "tool");
                functionResponseNode.put("name", functionName);
                functionResponseNode.put("content", functionResponse.toString());
                messages.remove(messages.size() - 1);
                //messages.add(functionResponseNode);
            }

            //return runConversation(messages, tools);
            String jsonString = "{\"message\":{\"role\":\"user\",\"content\":\"歌曲已开始播放，请欣赏。\"}}";
            return objectMapper.readTree(jsonString).get("message");
        } else {
            return responseMessage;
        }
    }

    public static String generateText(String prompt) throws IOException {
        //Log.i("ChatAPI", "prompt: " + prompt);
        ObjectNode userMessage = objectMapper.createObjectNode();
        userMessage.put("role", "user");
        userMessage.put("content", prompt);
        messages.add(userMessage);

        List<JsonNode> tools =getTools();
        JsonNode response = runConversation(messages, tools);
        String r = response.get("content").asText();
        //Log.i("ChatAPI", "r: " + r);
        ObjectNode assistantMessage = objectMapper.createObjectNode();
        assistantMessage.put("role", "assistant");
        assistantMessage.put("content", r);
        messages.add(assistantMessage);
        return r;
    }

    private static List<JsonNode> getTools() {
       return new ArrayList<>();//CustomTools.getTools();
    }


    private static Object callFunction(String functionName, JsonNode functionArgs) {
        try {
            // 指定要调用的类
            Class<?> clazz = CustomTools.class;

            // 获取所有静态方法
            Method[] methods = clazz.getDeclaredMethods();
            Method methodToInvoke = null;

            // 查找匹配的方法
            for (Method method : methods) {
                if (method.getName().equals(functionName)) {
                    methodToInvoke = method;
                    break;
                }
            }

            if (methodToInvoke == null) {
                throw new NoSuchMethodException("No such method: " + functionName);
            }

            // 获取方法参数类型
            Class<?>[] parameterTypes = methodToInvoke.getParameterTypes();
            Object[] args = new Object[parameterTypes.length];

            // 处理 functionArgs 字段
            if (functionArgs.isTextual()) {
                // 如果 functionArgs 是一个 JSON 字符串
                ObjectMapper objectMapper = new ObjectMapper();
                JsonNode parsedArgs = objectMapper.readTree(functionArgs.asText());

                // 将解析后的参数赋值给 args
                for (int index = 0; index < parameterTypes.length; index++) {
                    Class<?> paramType = parameterTypes[index];
                    String paramName = methodToInvoke.getParameters()[index].getName(); // 获取参数名称

                    // 调试输出类型和参数
                    System.out.println("Parameter type: " + paramType.getName() + ", Parameter name: " + paramName);

                    // 根据参数类型进行转换
                    if (paramType == String.class) {
                        args[index] = parsedArgs.has(paramName) ? parsedArgs.get(paramName).asText() : null;
                    } else if (paramType == Integer.class || paramType == int.class) {
                        args[index] = parsedArgs.has(paramName) ? parsedArgs.get(paramName).asInt() : null;
                    } else if (paramType == Double.class || paramType == double.class) {
                        args[index] = parsedArgs.has(paramName) ? parsedArgs.get(paramName).asDouble() : null;
                    } else if (paramType == Boolean.class || paramType == boolean.class) {
                        args[index] = parsedArgs.has(paramName) ? parsedArgs.get(paramName).asBoolean() : null;
                    } else if (paramType == JsonNode.class) {
                        args[index] = parsedArgs.has(paramName) ? parsedArgs.get(paramName) : null;
                    } else {
                        throw new IllegalArgumentException("Unsupported parameter type: " + paramType);
                    }
                }
            }

            // 调用静态方法
            return methodToInvoke.invoke(null, args);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
}
