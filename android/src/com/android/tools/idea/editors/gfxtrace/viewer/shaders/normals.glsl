//! COMMON
varying vec3 vNormal;

//! VERTEX
attribute vec3 aVertexPosition;
attribute vec3 aVertexNormal;

uniform mat4 uModelViewProj;
uniform float uInvertNormals;

void main(void) {
  vNormal = uInvertNormals * aVertexNormal;
  gl_Position = uModelViewProj * vec4(aVertexPosition, 1.0);
}

//! FRAGMENT
void main(void) {
  vec3 normal = normalize(vNormal);
  gl_FragColor = vec4((normal + 1.0) / 2.0, 1);
}
