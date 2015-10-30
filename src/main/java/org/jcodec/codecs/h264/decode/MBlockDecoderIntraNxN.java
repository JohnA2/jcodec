package org.jcodec.codecs.h264.decode;

import org.jcodec.codecs.h264.DecodedMBlock;
import org.jcodec.codecs.h264.EncodedMBlock;
import org.jcodec.codecs.h264.H264Const;
import org.jcodec.codecs.h264.decode.aso.Mapper;
import org.jcodec.codecs.h264.io.model.SliceHeader;
import org.jcodec.common.model.Picture8Bit;

/**
 * A decoder for I16x16 macroblocks
 * 
 * @author The JCodec project
 */
public class MBlockDecoderIntraNxN extends MBlockDecoderBase {
    private Mapper mapper;
    private Intra8x8PredictionBuilder prediction8x8Builder;

    public MBlockDecoderIntraNxN(Mapper mapper, SliceHeader sh, int poc,
            DecoderState decoderState) {
        super(sh, poc, decoderState);
        this.mapper = mapper;
        this.prediction8x8Builder = new Intra8x8PredictionBuilder();
    }

    public void decode(EncodedMBlock mBlock, DecodedMBlock mb) {

        int mbX = mapper.getMbX(mBlock.mbIdx);
        int mbY = mapper.getMbY(mBlock.mbIdx);
        int mbAddr = mapper.getAddress(mBlock.mbIdx);
        boolean leftAvailable = mapper.leftAvailable(mBlock.mbIdx);
        boolean topAvailable = mapper.topAvailable(mBlock.mbIdx);
        boolean topLeftAvailable = mapper.topLeftAvailable(mBlock.mbIdx);
        boolean topRightAvailable = mapper.topRightAvailable(mBlock.mbIdx);

        if (mBlock.cbpLuma() > 0 || mBlock.cbpChroma() > 0) {
            s.qp = (s.qp + mBlock.mbQPDelta + 52) % 52;
        }
        mb.setQp(0, s.qp);
        mb.setType(mBlock.curMbType);
        mb.setTransform8x8Used(mBlock.transform8x8Used);

        residualLuma(mBlock, leftAvailable, topAvailable, mbX, mbY);

        if (!mBlock.transform8x8Used) {
            for (int i = 0; i < 16; i++) {
                int blkX = (i & 3) << 2;
                int blkY = i & ~3;

                int bi = H264Const.BLK_INV_MAP[i];
                boolean trAvailable = ((bi == 0 || bi == 1 || bi == 4) && topAvailable)
                        || (bi == 5 && topRightAvailable) || bi == 2 || bi == 6 || bi == 8 || bi == 9 || bi == 10
                        || bi == 12 || bi == 14;

                Intra4x4PredictionBuilder.predictWithMode(mBlock.lumaModes[bi], mBlock.ac[0][bi],
                        blkX == 0 ? leftAvailable : true, blkY == 0 ? topAvailable : true, trAvailable, s.leftRow[0],
                        s.topLine[0], s.topLeft[0], (mbX << 4), blkX, blkY, mb.getPixels().getPlaneData(0));
            }
        } else {
            for (int i = 0; i < 4; i++) {
                int blkX = (i & 1) << 1;
                int blkY = i & 2;

                boolean trAvailable = (i == 0 && topAvailable) || (i == 1 && topRightAvailable) || i == 2;
                boolean tlAvailable = i == 0 ? topLeftAvailable : (i == 1 ? topAvailable : (i == 2 ? leftAvailable
                        : true));

                prediction8x8Builder.predictWithMode(mBlock.lumaModes[i], mBlock.ac[0][i],
                        blkX == 0 ? leftAvailable : true, blkY == 0 ? topAvailable : true, tlAvailable, trAvailable,
                        s.leftRow[0], s.topLine[0], s.topLeft[0], (mbX << 4), blkX << 2, blkY << 2, mb.getPixels().getPlaneData(0));
            }
        }

        int qp1 = calcQpChroma(s.qp, s.chromaQpOffset[0]);
        int qp2 = calcQpChroma(s.qp, s.chromaQpOffset[1]);
        
        decodeChroma(mBlock, mbX, mbY, leftAvailable, topAvailable, mb.getPixels(), qp1, qp2);
        
        mb.setQp(1, qp1);
        mb.setQp(2, qp2);

        MBlockDecoderUtils.collectChromaPredictors(s, mb.getPixels(), mbX);

//        MBlockDecoderUtils.saveMvsIntra(di, mbX, mbY);
        MBlockDecoderUtils.saveVectIntra(s, mapper.getMbX(mbAddr));
    }
}