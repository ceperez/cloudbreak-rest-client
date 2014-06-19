package com.sequenceiq.cloudbreak.client

import groovy.json.JsonSlurper
import groovy.text.SimpleTemplateEngine
import groovy.text.TemplateEngine
import groovy.util.logging.Slf4j
import groovyx.net.http.ContentType
import groovyx.net.http.RESTClient

@Slf4j
class CloudbreakClient {

    def private enum Resource {
        CREDENTIALS("credential", "credentials.json"),
        TEMPLATES("template", "template.json"),
        STACKS("stack", "stack.json"),
        BLUEPRINTS("blueprint", "blueprint.json"),
        CLUSTERS("stack/stack-id/cluster", "cluster.json")
        def path
        def template

        Resource(path, template) {
            this.path = path
            this.template = template
        }

        def String path() {
            return this.path
        }

        def String template() {
            return this.template
        }
    }

    def RESTClient restClient;
    def TemplateEngine engine = new SimpleTemplateEngine()
    def slurper = new JsonSlurper()


    CloudbreakClient(host = 'localhost', port = '8080', user = 'user@seq.com', password = 'test123') {
        restClient = new RESTClient("http://${host}:${port}/" as String)
        restClient.headers['Authorization'] = 'Basic ' + "$user:$password".getBytes('iso-8859-1').encodeBase64()
    }

    def String postCredentials() {
        log.debug("Posting credentials ...")
        def binding = [:]
        def response = processPost(Resource.CREDENTIALS, binding)
        log.debug("Got response: {}", response.data.id)
        return response?.data?.id
    }

    def String postTemplate(String name) {
        log.debug("Posting template ...")
        def binding = ["NAME": "$name"]
        def response = processPost(Resource.TEMPLATES, binding)
        log.debug("Got response: {}", response.data.id)
        return response?.data?.id
    }

    def String postStack(String stackName, String nodeCount) {
        log.debug("Posting stack ...")
        def binding = ["NODE_COUNT": nodeCount, "STACK_NAME": stackName]
        def response = processPost(Resource.STACKS, binding)
        log.debug("Got response: {}", response.data.id)
        return response?.data?.id
    }

    def String postBlueprint(String blueprint) {
        log.debug("Posting blueprint ...")
        def binding = ["BLUEPRINT": blueprint]
        def response = processPost(Resource.BLUEPRINTS, binding)
        log.debug("Got response: {}", response.data.id)
        return response?.data?.id
    }

    def void postCluster(String clusterName, Integer blueprintId, Integer stackId) {
        log.debug("Posting cluster ...")
        def binding = ["CLUSTER_NAME": clusterName, "BLUEPRINT_ID": blueprintId]
        def json = createJson(Resource.CLUSTERS.template(), binding)
        String path = Resource.CLUSTERS.path().replaceFirst("stack-id", stackId.toString())
        def Map postCtx = createPostRequestContext(path, ['json': json])
        def response = doPost(postCtx)
    }

    def boolean health() {
        log.debug("Checking health ...")
        Map getCtx = createGetRequestContext('health', null)
        Object healthObj = doGet(getCtx)
        return healthObj.data.status == 'ok'
    }

    def List<Map> getCredentials() {
        log.debug("Getting credentials...")
        getAllAsList(Resource.CREDENTIALS)
    }

    def List<Map> getBlueprints() {
        log.debug("Getting blueprints...")
        getAllAsList(Resource.BLUEPRINTS)
    }

    def List<Map> getTemplates() {
        log.debug("Getting templates...")
        getAllAsList(Resource.TEMPLATES)
    }

    def Object getCredential(String id) {
        log.debug("Getting credentials...")
        return getOne(Resource.CREDENTIALS, id)
    }

    def Object getTemplate(String id) {
        log.debug("Getting credentials...")
        return getOne(Resource.TEMPLATES, id)
    }

    def Object getBlueprint(String id) {
        log.debug("Getting credentials...")
        return getOne(Resource.BLUEPRINTS, id)
    }

    def private List getAllAsList(Resource resource) {
        Map getCtx = createGetRequestContext(resource.path(), [:]);
        Object response = doGet(getCtx);
        return response?.data
    }

    def private Object getOne(Resource resource, String id) {
        String path = resource.path() + "/$id"
        Map getCtx = createGetRequestContext(path, [:]);
        Object response = doGet(getCtx)
        return response?.data
    }

    def private Object doGet(Map getCtx) {
        Object response = null;
        try {
            response = restClient.get(getCtx)
        } catch (e) {
            log.error("ERROR: {}", e)
        }
        return response;
    }

    def private Object doPost(Map postCtx) {
        Object response = null;
        try {
            response = restClient.post(postCtx)
        } catch (e) {
            log.error("ERROR: {}", e)
        }
        return response;
    }

    def private Map createGetRequestContext(String resourcePath, Map ctx) {
        Map context = [:]
        String uri = "${restClient.uri}$resourcePath"
        context.put('path', uri)
        return context
    }

    def private Map createPostRequestContext(String resourcePath, Map ctx) {
        def Map<String, ?> putRequestMap = [:]
        def String uri = "${restClient.uri}$resourcePath"
        putRequestMap.put('path', uri)
        putRequestMap.put('body', ctx.get("json"));
        putRequestMap.put('requestContentType', ContentType.JSON)
        return putRequestMap
    }

    def private String createJson(String templateName, Map bindings) {
        def InputStream inPut = this.getClass().getClassLoader().getResourceAsStream("templates/${templateName}");
        String json = engine.createTemplate(new InputStreamReader(inPut)).make(bindings);
        return json;
    }

    def private Object processPost(Resource resource, Map binding) {
        def json = createJson(resource.template(), binding)
        def Map postCtx = createPostRequestContext(resource.path(), ['json': json])
        return doPost(postCtx)
    }
}
