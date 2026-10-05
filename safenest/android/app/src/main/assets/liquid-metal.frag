#ifdef GL_FRAGMENT_PRECISION_HIGH
precision highp float;
#else
precision mediump float;
#endif
uniform vec2 u_resolution;
uniform float u_time;
uniform vec2 u_mouse;

// Shared by WebGL and Android OpenGL ES 2.0. All colors are linear RGB inputs.
float surface(vec2 p, float t) {
  return sin(p.x * 2.6 + sin(p.y * 1.5 + t * 0.25) * 1.5 + t * 0.22)
       + sin(p.y * 3.2 - p.x * 1.1 - t * 0.18) * 0.48
       + sin(p.x * 4.1 + p.y * 2.0 + t * 0.12) * 0.18;
}
void main() {
  vec2 p = (gl_FragCoord.xy * 2.0 - u_resolution) / max(u_resolution.y, 1.0);
  vec2 mouse = (u_mouse * 2.0 - u_resolution) / max(u_resolution.y, 1.0);
  p += mouse * 0.055;
  p.x += sin(p.y * 1.1 - u_time * 0.11) * 0.45;
  p.y += sin(p.x * 0.8 + u_time * 0.09) * 0.25;
  float wave = surface(p, u_time);
  float fold = sin(wave * 2.2 + p.y * 0.45);
  float ridge = pow(1.0 - abs(sin(wave * 2.4 + p.x * 0.22)), 5.0);
  vec3 babyPink = vec3(1.00, 0.78, 0.88);
  vec3 shadowPlum = vec3(0.42, 0.20, 0.34);
  vec3 opalLavender = vec3(0.88, 0.78, 1.00);
  vec3 pearlWhite = vec3(1.00, 0.96, 0.98);
  vec3 pureWhite = vec3(1.00);
  float dx = (surface(p + vec2(0.035, 0.0), u_time) - wave) / 0.035;
  float dy = (surface(p + vec2(0.0, 0.035), u_time) - wave) / 0.035;
  vec3 normal = normalize(vec3(-dx, -dy, 0.85));
  vec3 reflection = reflect(vec3(0.0, 0.0, -1.0), normal);
  float studio = 0.5 + 0.5 * sin(reflection.y * 7.0 + reflection.x * 2.1);
  float highlight = pow(studio, 12.0);
  float seam = pow(1.0 - studio, 6.0);
  vec3 body = mix(shadowPlum, babyPink, smoothstep(-1.2, 0.6, wave + fold * 0.4));
  body = mix(body, shadowPlum, seam * 0.6);
  body = mix(body, opalLavender, clamp(ridge * 0.45, 0.0, 1.0));
  body = mix(body, pureWhite, clamp(highlight * 0.88 + ridge * 0.35, 0.0, 1.0));
  // A pearl base keeps the whole canvas baby pink rather than nearly black.
  vec3 color = mix(pearlWhite, body, 0.72);
  gl_FragColor = vec4(clamp(color, 0.0, 1.0), 1.0);
}
