@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package pe.edu.unmsm.siderurgica.ui.common

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.text.Normalizer
import java.util.Locale

data class SelectorOption(val id:String,val title:String,val subtitle:String="",val keywords:String="")
private fun searchable(value:String)=Normalizer.normalize(value,Normalizer.Form.NFD)
    .replace(Regex("\\p{M}+"),"").lowercase(Locale.ROOT).trim()

/** A compact field opens a searchable, bounded, lazy list; selection always stores the ID. */
@Composable fun SearchableSelector(label:String,selectedId:String,options:List<SelectorOption>,
    onSelect:(String)->Unit,enabled:Boolean=true,emptyText:String="No hay registros disponibles."){
    var open by remember{mutableStateOf(false)}
    var query by remember{mutableStateOf("")}
    val selected=options.firstOrNull{it.id==selectedId}
    ExposedDropdownMenuBox(expanded=open,onExpandedChange={expanded->if(enabled){query="";open=expanded}}){
        OutlinedTextField(value=selected?.title.orEmpty(),onValueChange={},readOnly=true,enabled=enabled,
            label={Text(label)},placeholder={Text("Seleccionar")},singleLine=true,
            trailingIcon={ExposedDropdownMenuDefaults.TrailingIcon(expanded=open)},
            modifier=Modifier.fillMaxWidth().menuAnchor())
    }
    if(selected!=null && selected.subtitle.isNotBlank())Text(selected.subtitle,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
    if(open)AlertDialog(onDismissRequest={open=false},title={Text("Seleccionar ${label.lowercase()}")},text={
        Column(verticalArrangement=Arrangement.spacedBy(12.dp)){
            OutlinedTextField(query,{query=it},Modifier.fillMaxWidth(),label={Text("Buscar por nombre, código o RUC")},singleLine=true)
            val filtered=remember(options,query){
                val terms=searchable(query).split(Regex("\\s+")).filter{it.isNotBlank()}
                options.filter{option->val haystack=searchable("${option.title} ${option.subtitle} ${option.keywords} ${option.id}");terms.all{it in haystack}}
            }
            Text("${filtered.size} resultados",style=MaterialTheme.typography.labelMedium)
            if(options.isEmpty())Text(emptyText)
            else if(filtered.isEmpty())Text("No hay coincidencias. Prueba con otro nombre o código.")
            else LazyColumn(Modifier.fillMaxWidth().heightIn(max=320.dp)){
                items(filtered,key={it.id}){option->
                    TextButton(onClick={onSelect(option.id);open=false;query=""},modifier=Modifier.fillMaxWidth()){
                        Column(Modifier.fillMaxWidth().padding(vertical=6.dp)){
                            Text(if(option.id==selectedId)"✓ ${option.title}" else option.title,style=MaterialTheme.typography.bodyLarge)
                            if(option.subtitle.isNotBlank())Text(option.subtitle,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    HorizontalDivider()
                }
            }
        }
    },confirmButton={TextButton(onClick={open=false}){Text("Cerrar")}})
}
