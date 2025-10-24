<#ftl output_format="plainText" encoding="UTF-8" strict_syntax=false auto_esc=false strip_whitespace=true>
<#--noinspection ALL-->
####################################################
# Query: ${headers.exchange_id}
# Templated At: ${.now?iso_local_ms}
# Resource: ${headers.CamelHttpUri}
####################################################
CONSTRUCT {
?targetThing ?pred ?object .
}
WHERE {
?targetThing a <${body.type}> ;
    <http://cambridgesemantics.com/ontologies/OpenSky#p_Origin_Country> "${body.name}" ;
    ?pred ?object .
}
LIMIT 100