// A triangle over the whole mip level of the atlas, for the classic GL programs.

#ifdef ALBEDO_FIXED_FUNCTION
void main() {
    gl_Position = gl_Vertex;
}
#else
void main() {
    vec2 uv = vec2((gl_VertexID << 1) & 2, gl_VertexID & 2);
    gl_Position = vec4(uv * 2.0 - 1.0, 0.0, 1.0);
}
#endif
