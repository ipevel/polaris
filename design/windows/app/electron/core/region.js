'use strict';
/**
 * 从节点名推断地区（对应 Android 端 utils/NodeCountry.kt 的精简版）。
 * 优先识别国旗 emoji，其次关键词表；识别不出返回 null，由 UI 归到「其他」。
 */

const FLAG_RE = /[\u{1F1E6}-\u{1F1FF}]{2}/u;

function fromFlag(name) {
  const m = String(name || '').match(FLAG_RE);
  if (!m) return null;
  const code = String.fromCodePoint(...[...m[0]].map((c) => c.codePointAt(0) - 0x1F1E6 + 65));
  return FLAG_LABEL[code] ? { code, label: FLAG_LABEL[code] } : null;
}

// 国旗 → 中文名（面板节点用得最多的几十个，其余交给关键词表）
const FLAG_LABEL = {
  HK: '香港', TW: '台湾', MO: '澳门', JP: '日本', KR: '韩国', SG: '新加坡',
  US: '美国', GB: '英国', UK: '英国', DE: '德国', FR: '法国', NL: '荷兰',
  CA: '加拿大', AU: '澳大利亚', RU: '俄罗斯', TR: '土耳其', IN: '印度',
  ID: '印度尼西亚', MY: '马来西亚', TH: '泰国', VN: '越南', PH: '菲律宾',
  BR: '巴西', AR: '阿根廷', IT: '意大利', ES: '西班牙', PT: '葡萄牙',
  SE: '瑞典', NO: '挪威', FI: '芬兰', DK: '丹麦', CH: '瑞士', AT: '奥地利',
  BE: '比利时', IE: '爱尔兰', PL: '波兰', CZ: '捷克', UA: '乌克兰',
  AE: '阿联酋', SA: '沙特', IL: '以色列', ZA: '南非', NZ: '新西兰',
  MX: '墨西哥', CL: '智利', CO: '哥伦比亚',
};

// 关键词 → 中文名。长词在前，避免「香港」被「港」抢先匹配。
const KEYWORDS = [
  ['香港', 'HK'], ['HongKong', 'HK'], ['Hong Kong', 'HK'], ['深港', 'HK'], ['沪港', 'HK'], ['京港', 'HK'], ['广港', 'HK'],
  ['台湾', 'TW'], ['台北', 'TW'], ['台中', 'TW'], ['新北', 'TW'], ['彰化', 'TW'], ['Taiwan', 'TW'],
  ['澳门', 'MO'], ['Macao', 'MO'], ['Macau', 'MO'],
  ['日本', 'JP'], ['东京', 'JP'], ['大阪', 'JP'], ['埼玉', 'JP'], ['沪日', 'JP'], ['深日', 'JP'], ['京日', 'JP'],
  ['韩国', 'KR'], ['首尔', 'KR'], ['韩', 'KR'], ['Korea', 'KR'], ['Seoul', 'KR'],
  ['新加坡', 'SG'], ['狮城', 'SG'], ['沪新', 'SG'], ['深新', 'SG'], ['Singapore', 'SG'],
  ['美国', 'US'], ['洛杉矶', 'US'], ['圣何塞', 'US'], ['西雅图', 'US'], ['纽约', 'US'], ['芝加哥', 'US'],
  ['凤凰城', 'US'], ['达拉斯', 'US'], ['Ashburn', 'US'], ['俄勒冈', 'US'], ['硅谷', 'US'],
  ['United States', 'US'], ['America', 'US'], ['USA', 'US'],
  ['英国', 'GB'], ['伦敦', 'GB'], ['United Kingdom', 'GB'], ['England', 'GB'],
  ['德国', 'DE'], ['法兰克福', 'DE'], ['柏林', 'DE'], ['Germany', 'DE'],
  ['法国', 'FR'], ['巴黎', 'FR'], ['France', 'FR'],
  ['荷兰', 'NL'], ['阿姆斯特丹', 'NL'], ['Netherlands', 'NL'],
  ['加拿大', 'CA'], ['多伦多', 'CA'], ['温哥华', 'CA'], ['Canada', 'CA'],
  ['澳大利亚', 'AU'], ['悉尼', 'AU'], ['墨尔本', 'AU'], ['Australia', 'AU'],
  ['俄罗斯', 'RU'], ['莫斯科', 'RU'], ['Russia', 'RU'],
  ['土耳其', 'TR'], ['伊斯坦布尔', 'TR'], ['Turkey', 'TR'],
  ['印度', 'IN'], ['孟买', 'IN'], ['India', 'IN'],
  ['印尼', 'ID'], ['印度尼西亚', 'ID'], ['雅加达', 'ID'], ['Indonesia', 'ID'],
  ['马来西亚', 'MY'], ['吉隆坡', 'MY'], ['Malaysia', 'MY'],
  ['泰国', 'TH'], ['曼谷', 'TH'], ['Thailand', 'TH'],
  ['越南', 'VN'], ['Vietnam', 'VN'],
  ['菲律宾', 'PH'], ['Philippines', 'PH'],
  ['巴西', 'BR'], ['圣保罗', 'BR'], ['Brazil', 'BR'],
  ['阿根廷', 'AR'], ['Argentina', 'AR'],
  ['意大利', 'IT'], ['米兰', 'IT'], ['Italy', 'IT'],
  ['西班牙', 'ES'], ['Spain', 'ES'],
  ['瑞典', 'SE'], ['Sweden', 'SE'],
  ['芬兰', 'FI'], ['Finland', 'FI'],
  ['瑞士', 'SW'], ['苏黎世', 'SW'], ['Switzerland', 'SW'],
  ['阿联酋', 'AE'], ['迪拜', 'AE'],
];

// 英文/拉丁别名 —— 面板节点里非常常见。
// 注意子串冲突（India ⊂ Indonesia、US ⊂ Russia…），所以按长度倒序匹配：
// 先试长词，短词只在长词没命中时生效。
const LATIN = [
  ['Indonesia', 'ID'], ['India', 'IN'],
  ['Netherlands', 'NL'], ['New Zealand', 'NZ'],
  ['United Kingdom', 'GB'], ['United States', 'US'], ['United Arab Emirates', 'AE'],
  ['Hong Kong', 'HK'], ['HongKong', 'HK'], ['Hongkong', 'HK'],
  ['Singapore', 'SG'], ['Shanghai', 'HK'], ['Shenzhen', 'HK'], ['Guangzhou', 'HK'],
  ['Kuala Lumpur', 'MY'], ['Ho Chi Minh', 'VN'],
  ['Istanbul', 'TR'], ['Johannesburg', 'ZA'],
  ['Australia', 'AU'], ['Austria', 'AT'], ['Argentina', 'AR'],
  ['America', 'US'], ['England', 'GB'], ['Canada', 'CA'], ['China', 'CN'],
  ['Colombia', 'CO'], ['Denmark', 'DK'], ['Finland', 'FI'], ['France', 'FR'],
  ['Germany', 'DE'], ['Ireland', 'IE'], ['Italy', 'IT'], ['Japan', 'JP'],
  ['Korea', 'KR'], ['Mexico', 'MX'], ['Nepal', 'NP'], ['Netherland', 'NL'],
  ['Norway', 'NO'], ['Poland', 'PL'], ['Portugal', 'PT'], ['Russia', 'RU'],
  ['Spain', 'ES'], ['Sweden', 'SE'], ['Switzerland', 'CH'], ['Thailand', 'TH'],
  ['Turkey', 'TR'], ['Ukraine', 'UA'], ['Vietnam', 'VN'], ['Brazil', 'BR'],
  ['Chile', 'CL'], ['Israel', 'IL'], ['Belgium', 'BE'], ['Prague', 'CZ'],
  ['Ashburn', 'US'], ['Seattle', 'US'], ['Dallas', 'US'], ['Phoenix', 'US'],
  ['Oregon', 'US'], ['Chicago', 'US'], ['New York', 'US'], ['Miami', 'US'],
  ['Los Angeles', 'US'], ['San Jose', 'US'], ['Las Vegas', 'US'],
  ['London', 'GB'], ['Berlin', 'DE'], ['Paris', 'FR'], ['Amsterdam', 'NL'],
  ['Tokyo', 'JP'], ['Osaka', 'JP'], ['Seoul', 'KR'], ['Bangkok', 'TH'],
  ['Sydney', 'AU'], ['Melbourne', 'AU'], ['Toronto', 'CA'], ['Vancouver', 'CA'],
  ['Moscow', 'RU'], ['Mumbai', 'IN'], ['Jakarta', 'ID'], ['Manila', 'PH'],
  ['Helsinki', 'FI'], ['Oslo', 'NO'], ['Stockholm', 'SE'], ['Zurich', 'CH'],
  ['Milan', 'IT'], ['Madrid', 'ES'], ['Lisbon', 'PT'], ['Dublin', 'IE'],
  ['Frankfurt', 'DE'], ['Singapore1', 'SG'],
  ['ChinaTelecom', 'CN'], ['China Unicom', 'CN'], ['China Mobile', 'CN'],
  ['CN2', 'CN'], ['CU', 'CN'], ['CMI', 'CN'],
].sort((a, b) => b[0].length - a[0].length);

// 面板节点常用两字母国家码（"US 01"、"US-LA-02"、"HK-Premium"）。
// 必须大小写敏感 + 词边界，否则 lowercase 的 "in"/"it" 会误命中。
const CODE_MAP = {
  US: 'US', UK: 'GB', GB: 'GB', HK: 'HK', TW: 'TW', MO: 'MO', JP: 'JP', KR: 'KR',
  SG: 'SG', DE: 'DE', FR: 'FR', CA: 'CA', AU: 'AU', RU: 'RU', NL: 'NL', IT: 'IT',
  ES: 'ES', SE: 'SE', NO: 'NO', FI: 'FI', DK: 'DK', CH: 'CH', AT: 'AT', BE: 'BE',
  IE: 'IE', PL: 'PL', CZ: 'CZ', UA: 'UA', AE: 'AE', SA: 'SA', IL: 'IL', ZA: 'ZA',
  NZ: 'NZ', MX: 'MX', CL: 'CL', CO: 'CO', TH: 'TH', VN: 'VN', PH: 'PH', BR: 'BR',
  AR: 'AR', MY: 'MY', ID: 'ID', IN: 'IN', PT: 'PT', TR: 'TR', CN: 'CN', TW2: 'TW',
};
const CODE_RE = new RegExp(`\\b(${Object.keys(CODE_MAP).join('|')})\\b`);

const LABEL = Object.fromEntries(Object.entries(FLAG_LABEL));
for (const [kw, code] of KEYWORDS) if (!LABEL[code]) LABEL[code] = code;

function detect(name) {
  const n = String(name || '');
  const byFlag = fromFlag(n);
  if (byFlag) return byFlag;
  const lower = n.toLowerCase();
  for (const [kw, code] of LATIN) {
    if (lower.includes(kw.toLowerCase())) return { code, label: FLAG_LABEL[code] || code };
  }
  // 大写国家码（大小写敏感，避免 "in"/"it" 误命中）
  const code = n.match(CODE_RE);
  if (code) {
    const c = CODE_MAP[code[1]];
    return { code: c, label: FLAG_LABEL[c] || c };
  }
  for (const [kw, code2] of KEYWORDS) {
    if (lower.includes(kw.toLowerCase())) return { code: code2, label: FLAG_LABEL[code2] || code2 };
  }
  return null;
}

module.exports = { detect, FLAG_LABEL };
