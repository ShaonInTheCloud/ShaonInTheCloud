precision mediump float;
    uniform vec2 u_resolution;
    uniform float u_time;
    uniform sampler2D u_art;
    uniform vec2 u_artSize;
    void main(){
      vec2 st=gl_FragCoord.xy/u_resolution;
      float t=u_time*.90;
      float screenAspect=u_resolution.x/u_resolution.y;
      float artAspect=u_artSize.x/u_artSize.y;
      vec2 cover=vec2(min(1.,screenAspect/artAspect),min(1.,artAspect/screenAspect));
      // Show the full abstract artwork on portrait screens instead of a flat crop.
      cover=mix(vec2(1.),cover,smoothstep(1.,1.6,screenAspect));
      float breathing=.82+sin(t*.55)*.018;
      vec2 uv=(vec2(st.x,1.-st.y)-.5)*cover*breathing+.5;
      // Flow and breathing deform the supplied folds rather than replace them.
      // Taper displacement at the edges so the artwork never stretches there.
      vec2 flowRoom=vec2(1.)-abs(st-.5)*1.1;
      uv+=vec2(sin(st.y*4.+t)+sin(st.x*3.-t*.6),cos(st.x*4.-t*.8))*cover*flowRoom*.038;
      uv+=vec2(sin(t*.65),cos(t*.55))*cover*flowRoom*.050;
      vec3 art=texture2D(u_art,clamp(uv,0.,1.)).rgb;
      vec3 shadowPink=vec3(.76,.38,.53);
      // Preserve the reference's continuous gloss and fine highlight detail.
      vec3 chrome=max(pow(art,vec3(1.08))*vec3(.99,.72,.80),shadowPink);
      // Traveling reflections catch the existing bright folds, like the chrome logo.
      float reflection=smoothstep(.76,.98,art.r)*(.5+.5*sin(st.y*5.-st.x*2.+t*.8));
      chrome=mix(chrome,vec3(1.,.86,.92),reflection*.34);
      // Rosier at the top, baby-pink reflections below; no opaque page scrim.
      chrome=mix(chrome,shadowPink,st.y*.12);
      gl_FragColor=vec4(chrome,1.);
    }
