#pragma once

#include "shader.h"

class SolidShader : public Shader {
    RgbColor m_color;

public:
    SolidShader(uint8_t* pCursor, uint8_t* pEnd);
    ~SolidShader();

    void begin(Msg* pMsg) override;
    void apply(uint16_t pixelIndex, uint8_t *colorOut, uint8_t *colorIn) override;
    void end() override;
};