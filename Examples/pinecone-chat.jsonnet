
local maxTokens = if(std.extVar("keepMaxTokens") == true) then std.extVar("maxTokens") else 10000;
local preset = |||
                  Use the following pieces of context to answer the question at the end. If
                  you don't know the answer, just say that you don't know, don't try to make up an answer.
                |||;
local query = "Question: "+std.extVar("query");
local context = if(std.extVar("keepContext") == true) then std.extVar("context") else "";
local prompt = std.join("\n", [query, preset, context]);
{
    "maxTokens": maxTokens,
    "topK": 8,
    "query": query,
    "preset" : preset,
    "context": context,
    "prompt": if(std.length(prompt) > maxTokens) then std.substr(prompt, std.abs(maxTokens - std.length(prompt)), maxTokens) else prompt,
    "services": ["pineconeService", "openAiService", "embeddingService", "openAiStreamService", "historyContextService"]
}