{"port": "${port}", "pigeonVersion": "${version}", "env": "${environment}", "group": "${group}","app": "${appName}","published": "${published}","online": "${online}", 
"weights": [
		<#list serviceWeights?keys as key>
			{
				"ip:port": "${key}",
				"weight": "${serviceWeights[key]}"
			}<#if key_has_next>,</#if>
		</#list>
		],
"services": [
<#list services as x>
	{
		"name": "${x.name}", 
		"published": "${x.published}",
		"type": "${x.type.name}",
		"methods": [
		<#list x.methods as m>
			{ 
				"name":"${m.name}",
				"parameterTypes": [
				<#list m.parameterTypes as p>
					"${p.name}"<#if p_has_next>,</#if>
				</#list>
				],
				"returnType": "${m.returnType.name}"
			}<#if m_has_next>,</#if>
		</#list>	
		]	
	}<#if x_has_next>,</#if>
</#list>
]
}
