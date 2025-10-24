export interface MetricTableData {
    name: string;
    minProcessingTime: number,
    maxProcessingTime: number,
    averageProcessingTime: number;
    successfulExchanges: number;
    failedExchanges: number;
    state: string;
    uptime: string;
  }