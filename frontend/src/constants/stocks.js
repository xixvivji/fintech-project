export const STOCK_OPTIONS = [
  { name: "삼성전자", code: "005930" },
  { name: "SK하이닉스", code: "000660" },
  { name: "NAVER", code: "035420" },
  { name: "카카오", code: "035720" },
  { name: "LG에너지솔루션", code: "373220" },
  { name: "현대차", code: "005380" },
  { name: "기아", code: "000270" },
  { name: "셀트리온", code: "068270" },
  { name: "삼성바이오로직스", code: "207940" },
  { name: "POSCO홀딩스", code: "005490" },
  { name: "KB금융", code: "105560" },
  { name: "신한지주", code: "055550" },
  { name: "하나금융지주", code: "086790" },
  { name: "우리금융지주", code: "316140" },
  { name: "삼성물산", code: "028260" },
  { name: "LG화학", code: "051910" },
  { name: "삼성SDI", code: "006400" },
  { name: "LG전자", code: "066570" },
  { name: "한화에어로스페이스", code: "012450" },
  { name: "HD현대중공업", code: "329180" },
  { name: "두산에너빌리티", code: "034020" },
  { name: "포스코퓨처엠", code: "003670" },
  { name: "에코프로비엠", code: "247540" },
  { name: "에코프로", code: "086520" },
  { name: "알테오젠", code: "196170" },
  { name: "HMM", code: "011200" },
  { name: "대한항공", code: "003490" },
  { name: "KT&G", code: "033780" },
  { name: "한국전력", code: "015760" },
  { name: "삼성전기", code: "009150" },
  { name: "SK이노베이션", code: "096770" },
];

export const PERIOD_OPTIONS = [
  { label: "6개월", value: 6 },
  { label: "1년", value: 12 },
  { label: "2년", value: 24 },
  { label: "3년", value: 36 },
  { label: "5년", value: 60 },
];

export function getStockNameByCode(code) {
  return STOCK_OPTIONS.find((s) => s.code === code)?.name || code;
}

