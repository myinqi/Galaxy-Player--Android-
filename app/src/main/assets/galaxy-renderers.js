"use strict";
(function(root){
  const FLEET=["explorer","destroyer","battlestar","venator","gothic_cruiser","borg_cube","bird_of_prey","basestar"];
  function reduced(){return matchMedia("(prefers-reduced-motion: reduce)").matches}
  function ship(ctx,effect,position,scale,angle,now,glow,color,thrust){
    const time=reduced()?0:now,size=Math.max(.65,scale);
    if(effect==="raven"&&typeof rabeZeichnen==="function"){
      rabeZeichnen(ctx,position,1.15*size,time,angle,glow,thrust?1:0,reduced()?{ohneWolke:true}:undefined);
      return;
    }
    if(effect==="cube"&&typeof galaxyCubeDraw==="function"){
      galaxyCubeDraw(ctx,position,1.15*size,time,glow,color);
      return;
    }
    if(FLEET.includes(effect)&&typeof galaxyFleetDraw==="function"&&galaxyFleetDraw(ctx,effect,position,1.05*size,angle,time,glow,color,thrust))return;
    ctx.save();ctx.translate(position[0],position[1]);ctx.rotate(angle);ctx.shadowColor="#68ddff";ctx.shadowBlur=12;ctx.fillStyle=color;ctx.beginPath();ctx.moveTo(16,0);ctx.lineTo(-11,-9);ctx.lineTo(-5,0);ctx.lineTo(-11,9);ctx.closePath();ctx.fill();ctx.restore();
  }
  function trail(ctx,effect,position,angle,color,now,active){
    if(!active)return;
    ctx.save();ctx.translate(position[0],position[1]);ctx.rotate(angle);ctx.globalCompositeOperation="lighter";
    if(effect==="nebula"){
      for(let i=0;i<9;i++){const u=i/8,x=-13-u*31,y=Math.sin(now/(620+i*95)+i*2.1)*7*u,r=3+7*u,cloud=ctx.createRadialGradient(x,y,0,x,y,r);cloud.addColorStop(0,color+"66");cloud.addColorStop(.45,color+"2c");cloud.addColorStop(1,color+"00");ctx.globalAlpha=(1-u)*(.72+.22*Math.sin(now/(430+i*80)));ctx.fillStyle=cloud;ctx.beginPath();ctx.arc(x,y,r,0,Math.PI*2);ctx.fill()}
    }else if(effect==="warp"){
      ctx.shadowColor=color;ctx.shadowBlur=14;for(let i=0;i<5;i++){ctx.globalAlpha=.16+i*.1;ctx.strokeStyle=color;ctx.lineWidth=.7+i*.45;ctx.beginPath();ctx.moveTo(-8,-5+i*2.5);ctx.quadraticCurveTo(-30-i*5,(-2+i)*1.4,-48-i*8,0);ctx.stroke()}ctx.globalAlpha=.75;ctx.lineWidth=1.2;ctx.beginPath();ctx.ellipse(0,0,19,10,0,0,Math.PI*2);ctx.stroke();
    }else if(effect==="comet"){
      const tail=ctx.createLinearGradient(-8,0,-62,0);tail.addColorStop(0,color+"dd");tail.addColorStop(1,color+"00");ctx.fillStyle=tail;ctx.shadowColor=color;ctx.shadowBlur=16;ctx.beginPath();ctx.moveTo(-6,-6);ctx.quadraticCurveTo(-36,-9,-65,0);ctx.quadraticCurveTo(-36,9,-6,6);ctx.closePath();ctx.fill();
    }else if(effect==="ion_storm"){
      ctx.strokeStyle=color;ctx.shadowColor=color;ctx.shadowBlur=11;ctx.lineWidth=1.4;for(let lane=-1;lane<=1;lane++){ctx.globalAlpha=.45+lane*.12;ctx.beginPath();ctx.moveTo(-7,lane*4);for(let i=1;i<=7;i++)ctx.lineTo(-7-i*7,lane*4+Math.sin(now/80+i*4.3+lane)*5);ctx.stroke()}
    }else if(effect==="plasma"){
      ctx.strokeStyle=color;ctx.shadowColor=color;ctx.shadowBlur=13;for(const y of[-4,4]){for(let i=5;i>=1;i--){ctx.globalAlpha=.1+i*.1;ctx.lineWidth=i;ctx.beginPath();ctx.moveTo(-7,y);ctx.lineTo(-25-i*5,y+Math.sin(now/100+y)*1.5);ctx.stroke()}}
    }else{
      ctx.shadowColor=color;ctx.shadowBlur=8;ctx.strokeStyle=color;ctx.lineWidth=3;ctx.beginPath();ctx.moveTo(-7,0);ctx.lineTo(-28-(reduced()?0:Math.random()*8),0);ctx.stroke();if(effect==="sparkle"&&!reduced()){ctx.fillStyle=color;for(let i=0;i<4;i++){ctx.globalAlpha=.25+Math.random()*.7;ctx.beginPath();ctx.arc(-17-Math.random()*22,(Math.random()-.5)*10,1+Math.random()*1.7,0,Math.PI*2);ctx.fill()}}
    }
    ctx.restore();
  }
  function scanner(ctx,effect,position,radius,color,now){
    const time=reduced()?0:now/1000,unit=1;
    ctx.save();ctx.translate(position[0],position[1]);ctx.strokeStyle=color;ctx.fillStyle=color;ctx.lineWidth=2*unit;ctx.shadowColor=color;ctx.shadowBlur=12*unit;
    if(effect==="rune"){
      ctx.rotate(time*.55);const size=radius+11*unit;ctx.setLineDash([5*unit,3*unit]);ctx.strokeRect(-size,-size,size*2,size*2);
    }else if(effect==="radar"){
      const r=radius+17*unit;ctx.globalAlpha=.7;ctx.beginPath();ctx.arc(0,0,r,0,Math.PI*2);ctx.stroke();ctx.rotate(time*1.7);const sweep=ctx.createRadialGradient(0,0,0,0,0,r);sweep.addColorStop(0,color+"aa");sweep.addColorStop(1,color+"00");ctx.fillStyle=sweep;ctx.globalAlpha=.35;ctx.beginPath();ctx.moveTo(0,0);ctx.arc(0,0,r,-.55,0);ctx.closePath();ctx.fill();
    }else if(effect==="targeting"){
      const r=radius+12*unit;ctx.rotate(-time*.35);for(let i=0;i<4;i++){ctx.rotate(Math.PI/2);ctx.beginPath();ctx.arc(0,0,r,-.42,.42);ctx.stroke();ctx.beginPath();ctx.moveTo(r-4*unit,0);ctx.lineTo(r+5*unit,0);ctx.stroke()}
    }else if(effect==="hexgrid"){
      const r=radius+13*unit;ctx.rotate(time*.25);for(let ring=1;ring<=2;ring++){ctx.globalAlpha=.85/ring;ctx.beginPath();for(let i=0;i<6;i++){const a=i*Math.PI/3,x=Math.cos(a)*r*ring*.62,y=Math.sin(a)*r*ring*.62;i?ctx.lineTo(x,y):ctx.moveTo(x,y)}ctx.closePath();ctx.stroke()}
    }else{
      const pulse=(time%1.8)/1.8,scanRadius=radius+(7+18*pulse)*unit;ctx.globalAlpha=.9*(1-pulse*.65);ctx.beginPath();ctx.arc(0,0,scanRadius,0,Math.PI*2);ctx.stroke();
    }
    ctx.restore();
  }
  function hueColor(hue,sat,light,alpha){
    return alpha==null?`hsl(${hue} ${sat}% ${light}%)`:`hsl(${hue} ${sat}% ${light}% / ${alpha})`;
  }
  function stillPlanet(ctx,radius,hue,visited,selected,ring){
    const r=Math.max(4,radius),tone=visited?260:hue;
    const gradient=ctx.createRadialGradient(-r*.35,-r*.4,1,0,0,r);
    gradient.addColorStop(0,visited?"hsl(260 4% 64%)":hueColor(tone,92,82));
    gradient.addColorStop(.42,visited?"hsl(260 6% 38%)":hueColor(tone,68,49));
    gradient.addColorStop(1,visited?"hsl(260 5% 20%)":hueColor(tone,70,13));
    ctx.fillStyle=gradient;ctx.shadowColor=visited?"#5c5666":hueColor(tone,80,62);ctx.shadowBlur=selected?18:7;
    ctx.beginPath();ctx.arc(0,0,r,0,Math.PI*2);ctx.fill();ctx.shadowBlur=0;
    if(ring){ctx.strokeStyle=visited?"rgba(145,140,153,.45)":`hsla(${tone},75%,75%,.55)`;ctx.beginPath();ctx.ellipse(0,0,r*1.7,r*.5,-.25,0,Math.PI*2);ctx.stroke()}
  }
  function livingRing(ctx,r,tone,visited,time,front){
    const rx=r*1.92,ry=r*.4,start=front?0:Math.PI,end=front?Math.PI:Math.PI*2,gain=front?1:.42;
    const mid=.5+.32*Math.sin(time/4.8+tone*.01),dim=visited?.14:.2,lit=visited?.3:.46;
    const sheen=ctx.createLinearGradient(-rx,0,rx,0);
    sheen.addColorStop(0,hueColor(tone,visited?6:16,visited?70:84,dim*gain));
    sheen.addColorStop(mid,hueColor(tone,visited?8:22,visited?78:90,lit*gain));
    sheen.addColorStop(1,hueColor(tone,visited?6:16,visited?70:84,dim*gain));
    ctx.save();ctx.rotate(-.26);ctx.strokeStyle=sheen;ctx.lineCap="butt";
    ctx.lineWidth=Math.max(1.6,r*.18);ctx.beginPath();ctx.ellipse(0,0,rx,ry,0,start,end);ctx.stroke();
    ctx.lineWidth=Math.max(1.05,r*.09);ctx.globalAlpha=.9;ctx.beginPath();ctx.ellipse(0,0,rx*.72,ry*.72,0,start,end);ctx.stroke();
    ctx.restore();
  }
  function livingPlanet(ctx,radius,hue,visited,selected,ring,now){
    const r=Math.max(4,radius),tone=visited?260:hue,sat=visited?6:68,time=now/1000,kind=hue%3;
    const spin=time*(.18+(hue%7)*.015)+hue*.01;
    if(selected){ctx.shadowColor=hueColor(tone,70,62,.9);ctx.shadowBlur=16}
    const halo=ctx.createRadialGradient(0,0,r*.55,0,0,r*1.45);
    halo.addColorStop(0,hueColor(tone,visited?8:70,55,0));halo.addColorStop(.62,hueColor(tone,visited?8:75,58,.16));halo.addColorStop(1,hueColor(tone,70,50,0));
    ctx.fillStyle=halo;ctx.beginPath();ctx.arc(0,0,r*1.45,0,Math.PI*2);ctx.fill();ctx.shadowBlur=0;
    if(ring)livingRing(ctx,r,tone,visited,time,false);
    ctx.save();ctx.beginPath();ctx.arc(0,0,r,0,Math.PI*2);ctx.clip();
    const body=ctx.createRadialGradient(-r*.12,-r*.14,r*.08,0,0,r);
    body.addColorStop(0,hueColor(tone,visited?5:78,visited?62:72));body.addColorStop(.5,hueColor(tone,sat,visited?36:46));body.addColorStop(1,hueColor(tone,visited?8:58,visited?18:22));
    ctx.fillStyle=body;ctx.fillRect(-r,-r,r*2,r*2);
    ctx.save();ctx.rotate(kind===1?.4:spin*.15);
    if(kind!==1){
      for(let i=-5;i<=5;i++){
        const y=i*r*.2+Math.sin(spin*1.4+i*1.1)*r*.05,h=r*(.07+.04*Math.abs(Math.sin(i+hue)));
        ctx.globalAlpha=.18+.08*((i+5)%2);ctx.fillStyle=hueColor(tone+(i%2?12:-10),visited?8:sat+8,visited?28:38+i*2);
        ctx.fillRect(-r,y-h/2,r*2,h);
      }
    }else{
      ctx.rotate(spin*.4);
      for(let i=0;i<6;i++){
        const a=i*1.2+hue,cx=Math.cos(a)*r*.28,cy=Math.sin(a*.9)*r*.32;
        ctx.globalAlpha=.28;ctx.fillStyle=hueColor(tone+18,visited?10:58,visited?30:34);
        ctx.beginPath();ctx.ellipse(cx,cy,r*(.28+i*.03),r*(.12+i*.02),a,0,Math.PI*2);ctx.fill();
      }
    }
    ctx.restore();
    ctx.globalAlpha=1;
    for(let i=0;i<4;i++){
      const drift=spin*(.7+i*.12)+i*1.7,cx=Math.cos(drift)*r*.42,cy=Math.sin(drift*.8+hue)*r*.28;
      ctx.globalAlpha=.16+.08*Math.sin(time+i);ctx.fillStyle=visited?"rgba(210,208,220,.2)":hueColor(tone,30,92,.28);
      ctx.beginPath();ctx.ellipse(cx,cy,r*(.32-i*.04),r*(.11+i*.015),.5+i*.2,0,Math.PI*2);ctx.fill();
    }
    ctx.globalAlpha=1;ctx.restore();
    if(ring)livingRing(ctx,r,tone,visited,time,true);
  }
  const WORLD_MAPS=["earth","mars","venus","jupiter","saturn","neptune","mercury"],worldImages={};
  function worldImage(name){
    if(!worldImages[name]){const img=new Image();img.src="/static/galaxy-worlds/"+name+(name==="sun"?".png":".jpg");worldImages[name]=img}
    return worldImages[name];
  }
  function preloadWorldMaps(){WORLD_MAPS.forEach(worldImage);worldImage("sun")}
  function worldMapReady(img){return!!(img&&img.complete&&img.naturalWidth)}
  function observatoryPlanet(ctx,radius,hue,visited,selected,ring,now){
    preloadWorldMaps();
    const r=Math.max(4,radius),tone=visited?260:hue,time=now/1000,kind=WORLD_MAPS[Math.abs(hue|0)%WORLD_MAPS.length],img=worldImage(kind),banded=kind==="saturn"||!!ring;
    if(selected){ctx.shadowColor=hueColor(tone,70,62,.9);ctx.shadowBlur=16}
    const halo=ctx.createRadialGradient(0,0,r*.55,0,0,r*1.45);
    halo.addColorStop(0,hueColor(tone,visited?8:40,55,0));halo.addColorStop(.62,hueColor(tone,visited?8:45,58,.12));halo.addColorStop(1,hueColor(tone,40,50,0));
    ctx.fillStyle=halo;ctx.beginPath();ctx.arc(0,0,r*1.45,0,Math.PI*2);ctx.fill();ctx.shadowBlur=0;
    if(banded)livingRing(ctx,r,tone,visited,time,false);
    ctx.save();ctx.beginPath();ctx.arc(0,0,r,0,Math.PI*2);ctx.clip();
    ctx.fillStyle=visited?"hsl(260 6% 42%)":hueColor(tone,38,48);ctx.fill();
    if(worldMapReady(img)){
      const height=r*2,width=height*2,shift=((time*r*.12+hue)%width+width)%width;
      ctx.drawImage(img,-shift-width,-r,width,height);ctx.drawImage(img,-shift,-r,width,height);ctx.drawImage(img,-shift+width,-r,width,height);
      if(visited){ctx.globalCompositeOperation="saturation";ctx.fillStyle="#7a7680";ctx.fill();ctx.globalCompositeOperation="source-over"}
    }
    ctx.restore();
    if(banded)livingRing(ctx,r,tone,visited,time,true);
  }
  function planet(ctx,effect,position,radius,opts){
    const settings=opts||{};
    ctx.save();ctx.translate(position[0],position[1]);
    if(effect==="living")livingPlanet(ctx,radius,settings.hue||0,!!settings.visited,!!settings.selected,!!settings.ring,reduced()?0:settings.now||0);
    else if(effect==="observatory")observatoryPlanet(ctx,radius,settings.hue||0,!!settings.visited,!!settings.selected,!!settings.ring,reduced()?0:settings.now||0);
    else stillPlanet(ctx,radius,settings.hue||0,!!settings.visited,!!settings.selected,!!settings.ring);
    ctx.restore();
  }
  function paintSunCorona(ctx,r,t){
    const breath=1+.045*Math.sin(t/2000*Math.PI);
    const outer=ctx.createRadialGradient(0,0,r*.35,0,0,r*2.4*breath);
    outer.addColorStop(0,"rgba(255,236,190,.16)");outer.addColorStop(.38,"rgba(232,176,255,.12)");outer.addColorStop(.72,"rgba(160,80,210,.05)");outer.addColorStop(1,"rgba(80,24,90,0)");
    ctx.fillStyle=outer;ctx.beginPath();ctx.arc(0,0,r*2.4*breath,0,Math.PI*2);ctx.fill();
    const mid=ctx.createRadialGradient(0,0,r*.15,0,0,r*1.55*breath);
    mid.addColorStop(0,"rgba(255,248,220,.32)");mid.addColorStop(.5,"rgba(255,214,150,.12)");mid.addColorStop(1,"rgba(255,190,120,0)");
    ctx.fillStyle=mid;ctx.beginPath();ctx.arc(0,0,r*1.55*breath,0,Math.PI*2);ctx.fill();
    ctx.globalCompositeOperation="lighter";
    const inner=ctx.createRadialGradient(-r*.12,-r*.14,0,0,0,r*1.08);
    inner.addColorStop(0,"rgba(255,250,230,.28)");inner.addColorStop(.55,"rgba(255,224,170,.08)");inner.addColorStop(1,"rgba(255,210,160,0)");
    ctx.fillStyle=inner;ctx.beginPath();ctx.arc(0,0,r*1.08,0,Math.PI*2);ctx.fill();
    ctx.globalCompositeOperation="source-over";
    return breath;
  }
  function sun(ctx,effect,position,radius,now,color){
    const r=Math.max(8,radius),t=reduced()?0:now||0;
    ctx.save();ctx.translate(position[0],position[1]);
    if(effect!=="living"&&effect!=="observatory"){
      ctx.fillStyle=color||"#fff2bd";ctx.shadowColor="#c976ff";ctx.shadowBlur=30;
      ctx.beginPath();ctx.arc(0,0,r,0,Math.PI*2);ctx.fill();ctx.restore();return;
    }
    const breath=paintSunCorona(ctx,r,t),disk=effect==="observatory"?worldImage("sun"):null;
    if(effect==="observatory")preloadWorldMaps();
    ctx.shadowColor="#c976ff";ctx.shadowBlur=effect==="observatory"?0:18*breath;
    if(effect==="observatory"&&worldMapReady(disk)){
      const span=r/.78;
      ctx.drawImage(disk,-span,-span,span*2,span*2);
    }else{
      const core=ctx.createRadialGradient(-r*.18,-r*.2,0,0,0,r);
      core.addColorStop(0,"#fffce8");core.addColorStop(.4,"#ffe08a");core.addColorStop(.82,"#f0c56a");core.addColorStop(1,"#e0a85c");
      ctx.fillStyle=core;ctx.beginPath();ctx.arc(0,0,r,0,Math.PI*2);ctx.fill();
    }
    ctx.shadowBlur=0;ctx.restore();
  }
  root.GalaxyRenderers={ship,trail,scanner,planet,sun};
})(window);
