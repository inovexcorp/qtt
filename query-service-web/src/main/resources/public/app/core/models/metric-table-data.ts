export interface MetricTableData {
    name: string;
    minProcessingTime: number,
    maxProcessingTime: number,
    averageProcessingTime: number;
    successfulExchanges: number;
    failedExchanges: number;
    /** False until the route handles an exchange; its processing times are placeholders until then. */
    hasExchanges: boolean;
    state: string;
    uptime: string;
  }