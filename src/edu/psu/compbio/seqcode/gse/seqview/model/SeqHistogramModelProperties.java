package edu.psu.compbio.seqcode.gse.seqview.model;


public class SeqHistogramModelProperties extends ModelProperties {

    public Integer BinWidth = 1;
    public Integer DeDuplicate = 100;
    public Boolean UseWeights = Boolean.TRUE;
    public Integer GaussianKernelVariance = 0;
    public Boolean ReadExtension = Boolean.FALSE;
    public Boolean ShowPairedReads = Boolean.TRUE;
    public Boolean ShowSingleReads = Boolean.TRUE;
}