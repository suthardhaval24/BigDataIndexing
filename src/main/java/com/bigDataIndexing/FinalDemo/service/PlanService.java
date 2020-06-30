package com.bigDataIndexing.FinalDemo.service;

/* dhaval created on 2/8/20 inside the package - com.bigDataIndexing.FinalDemo.service */

import com.bigDataIndexing.FinalDemo.eTag.EtagMap;
import com.bigDataIndexing.FinalDemo.exception.*;
import org.apache.http.HttpHost;
import org.elasticsearch.action.delete.DeleteRequest;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestHighLevelClient;
import org.everit.json.schema.Schema;
import org.everit.json.schema.ValidationException;
import org.everit.json.schema.loader.SchemaLoader;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class PlanService {

    @Autowired
    private TokenService tokenService;
    Map<String, String> mapTracker = new HashMap<>();

    private static JedisPool jedisPool = new JedisPool("localhost", 6379);
    private static Map<String, String> map = new HashMap<>();
    static String IndexQueue = "RedisIndexQueue";
    private RestHighLevelClient restHighLevelClient = new RestHighLevelClient(RestClient.builder(new HttpHost("localhost", 9200)).setRequestConfigCallback(requestConfigBuilder ->
            requestConfigBuilder
                    .setConnectTimeout(5000)
                    .setSocketTimeout(5000))
            .setMaxRetryTimeoutMillis(60000));

    public static Map<String, String> retrieveMap(JSONObject jsonData) {
        nestedObject(jsonData);
        return map;
    }

    public static Object nestedObject(JSONObject jsonNestedObject) {
        JSONObject object = new JSONObject();
        JSONArray array = new JSONArray();
        for (Object key : jsonNestedObject.keySet()) {
            if (jsonNestedObject.get((String) key) instanceof JSONObject) {
                object.put((String) key, nestedObject(jsonNestedObject.getJSONObject((String) key)));
            } else if (jsonNestedObject.get((String) key) instanceof JSONArray) {
                JSONArray arr = jsonNestedObject.getJSONArray((String) key);
                for (int i = 0; i < arr.length(); i++) {
                    array.put(i, nestedObject(arr.getJSONObject(i)));
                }
                object.put((String) key, array.toString());
            } else {
                object.put((String) key, jsonNestedObject.get((String) key));
            }
        }
        if (!object.keySet().isEmpty())
            map.put(jsonNestedObject.get("objectType") + "_" + jsonNestedObject.get("objectId"), object.toString());
        EtagMap.getEtags().put(jsonNestedObject.get("objectType") + "_" + jsonNestedObject.get("objectId"), UUID.randomUUID().toString());
        return jsonNestedObject.get("objectType") + "_" + jsonNestedObject.get("objectId");
    }

    public JSONObject readData(String planData) {
        Jedis jedis = jedisPool.getResource();
        JSONObject object = new JSONObject(planData);
        for (Object key : object.keySet()) {
            try {
                if (((String) object.get((String) key)).contains("[")) {
                    JSONArray array = new JSONArray((String) object.get((String) key));
                    for (int i = 0; i < array.length(); i++) {
                        array.put(i, readData(jedis.get((String) array.get(i))));
                    }
                    object.put((String) key, array);
                }
                if (jedis.get(object.getString((String) key)) != null) {
                    if (jedis.get((String) object.get((String) key)) != null) {
                        object.put((String) key, readData(jedis.get((String) object.get((String) key))));
                    }
                }
            } catch (Throwable ex) {
                System.out.println(ex.getMessage());
            }
        }
        jedis.close();
        return object;
    }

    public long deleteData(String planId) {
        Jedis jedis = jedisPool.getResource();
        System.out.println(jedis.get(planId));
        String id = "";
        String type = "";
        try {
            if (planId != null) {
                id = planId.split("_")[1];
                type = planId.split("_")[0];
            }
            restHighLevelClient.delete(new DeleteRequest("insurance_plan_index", "plan", id));
        } catch (IOException e) {
            System.out.println("Exception during elastic search delete" + e.getMessage());
        }

        if (jedis.get(planId) != null) {
            JSONObject object = new JSONObject(jedis.get(planId));
            for (Object key : object.keySet()) {
                if (String.valueOf(object.get((String) key)).contains("[")) {
                    JSONArray array = new JSONArray(String.valueOf(object.get((String) key)));
                    for (int i = 0; i < array.length(); i++) {
                        deleteData(array.getString(i));
                    }
                }
                if (jedis.get(String.valueOf(object.get((String) key))) != null) {
                    System.out.println(jedis.get(object.getString((String) key)));
                    deleteData(object.getString((String) key));
                }
            }
            long status = jedis.del(planId);
            jedis.close();
            return status;
        } else {
            return -2;
        }
    }

    public boolean validateJson(JSONObject jsonData) throws FileNotFoundException {
//        System.out.println("Inside validateschema");
        BufferedReader bufferedReader = new BufferedReader(new FileReader("src/main/resources/static/json_schema.json"));
        JSONObject jsonSchema = new JSONObject(
                new JSONTokener(bufferedReader));
        System.out.println(jsonSchema.toString());
        Schema schema = SchemaLoader.load(jsonSchema);
        try {
            schema.validate(jsonData);
            return true;
        } catch (ValidationException e) {
            throw new ExpectationFailed("Enter valid input! The issue is present in " + e.getMessage());
        }
    }

    public void removeEtags(String planId) {
        Set<String> set = EtagMap.getEtags().keySet()
                .stream()
                .filter(s -> s.startsWith(planId))
                .collect(Collectors.toSet());
        if (!set.isEmpty()) {
            EtagMap.getEtags().keySet().removeAll(set);
        }
    }

    public String getEtags(String planId) {
        String etag = EtagMap.getEtags().get(planId + "p");
        if (etag == null) {
            removeEtags(planId);
            etag = UUID.randomUUID().toString();
            EtagMap.getEtags().put(planId + "p", etag);
            EtagMap.getEtags().put(planId + "g", etag);
            return etag;
        }
        return etag;
    }

    public Map<String, String> createPlan(String token, String plan) {
        mapTracker.clear();
        if (tokenService.validateToken(token)) {
            Jedis jedis = null;
            JSONObject jsonData = new JSONObject(new JSONTokener((new JSONObject(plan)).toString()));
            try {
                if (validateJson(jsonData)) {
                    jedis = jedisPool.getResource();
                    String key = jsonData.get("objectType") + "_" + jsonData.get("objectId");
                    if (jedis.get(key) == null) {
                        Map<String, String> data = PlanService.retrieveMap(jsonData);
                        for (Map.Entry entry : data.entrySet()) {
                            jedis.set((String) entry.getKey(), (String) entry.getValue());
                        }
                        jedis.rpush(IndexQueue.getBytes(), jsonData.toString().getBytes(StandardCharsets.UTF_8));
                        String etag = UUID.randomUUID().toString();
                        EtagMap.getEtags().put(key + "p", etag);
                        mapTracker.put("planid", key);
                        mapTracker.put("etag", etag);
                        return mapTracker;
                    } else {
                        mapTracker.put("etag", EtagMap.getEtags().get(key + "p"));
                        return mapTracker;
                    }
                } else throw new ExpectationFailed("Enter correct input!");
            } catch (Exception e) {
                throw new BadRequest(e.getMessage());
            } finally {
                if (jedis != null)
                    jedis.close();
            }
        } else throw new BadRequest("Token is expired");
    }

    public Map<String, String> getPlan(String token, String id, String etag) {
        mapTracker.clear();
        if (!tokenService.validateToken(token)) throw new BadRequest("Token is expired");
        else {
            if (etag != null) {
                if (EtagMap.getEtags().containsKey(id + "g")) {
                    if (EtagMap.getEtags().get(id + "g").equals(etag))
//                        etag = EtagMap.getEtags().get(id+"g");
                        throw new NotModified("Plan has not been updated since last time!");
                    else {
                        etag = EtagMap.getEtags().get(id + "g");
                    }
                }
                String plan = jedisPool.getResource().get(id);
                if (plan == null) throw new ResourceNotFound("plan", "id", id);
                etag = etag != null ? etag : EtagMap.getEtags().get(id + "p");
                EtagMap.getEtags().put(id + "g", etag);
                mapTracker.put("plan", readData(plan).toString());
                mapTracker.put("etag", EtagMap.getEtags().get(id + "p"));
                return mapTracker;
            } else throw new PreconditionFailed("Etag is not present");
        }
    }

    public Map<String, String> updatePlan(String token, String planId, String etag, String plan) {
        Jedis jedis = jedisPool.getResource();
        if (!tokenService.validateToken(token)) throw new BadRequest("Token is expired");
        if (planId == null) throw new BadRequest("PlanId cannot be Empty");
        if (etag == null || etag.equals(""))
            throw new PreconditionFailed("Plan has been updated by other User. Please GET the updated Plan and then update it!");
        if (EtagMap.getEtags().containsKey(planId + "pu")) {
            if (etag.equals(EtagMap.getEtags().get(planId + "pu")))
                throw new NotModified("Data has not been updated since last time!");
        }
        if (EtagMap.getEtags().containsKey(planId + "p")) {
            if (!etag.equals(EtagMap.getEtags().get(planId + "p")))
                throw new Forbidden("Plan has been updated by other User. Please GET the updated Plan and then update it!");
            if (jedis.get(planId) != null) {
                if ((deleteData(planId)) > 0) {
                    removeEtags(planId);
                    mapTracker = createPlan(token, plan);
                    etag = UUID.randomUUID().toString();
                    EtagMap.getEtags().put(planId + "p", etag);
                    mapTracker.replace("etag", etag);
                    jedis.close();
                    return mapTracker;
                } else throw new BadRequest("Update Failed");
            } else throw new BadRequest("No such content found!!");

        } else
            throw new PreconditionFailed("Plan has been updated by other User. Please GET the updated Plan and then update it!");
    }
}
