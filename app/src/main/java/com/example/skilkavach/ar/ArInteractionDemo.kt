package com.example.skilkavach.ar

import androidx.compose.animation.core.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlin.math.abs

/** Interactive tutorial uses isolated state and never reports training actions. */
@Composable
fun ArInteractionDemo(onClose: () -> Unit) {
    var stage by rememberSaveable { mutableIntStateOf(0) }
    var tapped by rememberSaveable { mutableStateOf(false) }
    var swept by rememberSaveable { mutableStateOf(false) }
    var dragDistance by remember { mutableFloatStateOf(0f) }
    val transition=rememberInfiniteTransition(label="gesture demo")
    val phase by transition.animateFloat(0f,1f,infiniteRepeatable(tween(1800),RepeatMode.Reverse),label="gesture")
    val titles=listOf("1. Scan and place", "2. Tap a target", "3. Practice the sweep", "Ready to train")
    val instructions=listOf(
        "In AR, aim at a textured floor 1.8–4 metres ahead and move slowly. When the ring turns green, tap Place equipment. Auto-place puts equipment 2.5 metres ahead.",
        "Tap the green sign in this demo. In training, follow the current instruction and tap its labelled object.",
        "Drag sideways across this demo panel. In training, finish your drag on the Sweep label; a quick tap will not count.",
        "After placement, read the current step and tap its green target label. Use Fullscreen for more camera space, or Reposition if the scene is out of view. Demo is always available."
    )
    Dialog(onDismissRequest=onClose,properties=DialogProperties(usePlatformDefaultWidth=false)) {
        Surface(Modifier.fillMaxWidth().padding(16.dp),shape=RoundedCornerShape(24.dp),color=Color.White) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(20.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
                Text("AR interaction demo",style=MaterialTheme.typography.headlineSmall)
                Text("Tutorial only • does not count toward assessment",style=MaterialTheme.typography.labelMedium,color=Color(0xFF606873))
                LinearProgressIndicator(progress={(stage+1)/4f},modifier=Modifier.fillMaxWidth())
                Text(titles[stage],style=MaterialTheme.typography.titleLarge)
                Text(instructions[stage])
                Canvas(Modifier.fillMaxWidth().height(190.dp).background(Color(0xFF10283A),RoundedCornerShape(16.dp))
                    .pointerInput(stage) { detectTapGestures { point -> if(stage==1 && point.x in size.width*.22f..size.width*.78f && point.y in size.height*.28f..size.height*.70f) tapped=true } }
                    .pointerInput(stage) { detectDragGestures(onDragStart={dragDistance=0f},onDragEnd={if(stage==2 && abs(dragDistance)>size.width*.25f)swept=true}) { change, amount -> change.consume();dragDistance+=amount.x } }) {
                    val w=size.width;val h=size.height
                    for(i in 1..7)drawLine(Color(0xFF23465B),Offset(i*w/8,0f),Offset(i*w/8,h),1.dp.toPx())
                    for(i in 1..4)drawLine(Color(0xFF23465B),Offset(0f,i*h/5),Offset(w,i*h/5),1.dp.toPx())
                    when(stage) {
                        0->{val ready=phase>.55f;drawCircle(if(ready)Color(0xFF45D69B)else Color(0xFFFF796E),30.dp.toPx(),Offset(w/2,h/2),style=Stroke(4.dp.toPx()));drawCircle(Color.White,8.dp.toPx(),Offset(w*(.25f+.5f*phase),h*.8f))}
                        1->{drawRoundRect(if(tapped)Color(0xFF40C28A)else Color(0xFF08774B),Offset(w*.22f,h*.28f),androidx.compose.ui.geometry.Size(w*.56f,h*.42f),androidx.compose.ui.geometry.CornerRadius(10.dp.toPx()));drawLine(Color.White,Offset(w*.35f,h*.49f),Offset(w*.65f,h*.49f),8.dp.toPx());drawLine(Color.White,Offset(w*.57f,h*.37f),Offset(w*.65f,h*.49f),8.dp.toPx());drawLine(Color.White,Offset(w*.57f,h*.61f),Offset(w*.65f,h*.49f),8.dp.toPx());drawCircle(Color.White.copy(alpha=.5f),18.dp.toPx(),Offset(w*.5f,h*(.52f+.07f*phase)),style=Stroke(3.dp.toPx()))}
                        2->{drawLine(Color(0xFF65CFF3),Offset(w*.18f,h*.5f),Offset(w*.82f,h*.5f),6.dp.toPx());drawCircle(if(swept)Color(0xFF45D69B)else Color.White,16.dp.toPx(),Offset(w*(.18f+.64f*phase),h*.5f))}
                        else->{drawCircle(Color(0xFF08774B),52.dp.toPx(),center);drawLine(Color.White,Offset(w*.40f,h*.50f),Offset(w*.48f,h*.62f),7.dp.toPx());drawLine(Color.White,Offset(w*.48f,h*.62f),Offset(w*.64f,h*.35f),7.dp.toPx())}
                    }
                }
                Text(when(stage){1->if(tapped)"Correct — target selected" else "Try it: tap the green sign";2->if(swept)"Correct — sweep completed" else "Try it: drag across at least a quarter of the panel";0->"Animated example: red = scanning, green = ready";else->"Virtual equipment only. No real hazard detection."},color=Color(0xFF08774B))
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                    TextButton(onClick=onClose){Text("Close demo")}
                    if(stage>0)TextButton(onClick={stage--}){Text("Back")}
                    Button(onClick={if(stage==3)onClose()else stage++},enabled=stage!=1&&stage!=2 || stage==1&&tapped || stage==2&&swept){Text(if(stage==3)"Start training" else "Next")}
                }
            }
        }
    }
}
