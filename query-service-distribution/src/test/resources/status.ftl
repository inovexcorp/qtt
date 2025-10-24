<#ftl output_format="plainText" encoding="UTF-8" strict_syntax=false auto_esc=false strip_whitespace=true>
<#--noinspection ALL-->
{
"context_start_date": "${exchange.context.startDate?datetime}",
"context_name": "${exchange.context.name}",
"version": "${exchange.context.version}",
"uptime": "${exchange.context.uptime}",
"routes": [
<#list exchange.context.routes as route>
    {
        "id": "${route.routeId}",
        "uptime": "${route.uptime}",
        "desc": "${route.routeDescription!''}",
        "endpoint": "${route.endpoint}"
    }<#sep>,</#sep>
</#list>
}