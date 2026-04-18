import { Empty as AntEmpty, Button } from 'antd';
import { InboxOutlined } from '@ant-design/icons';

interface EmptyStateProps {
  description?: string;
  action?: {
    text: string;
    onClick: () => void;
  };
  image?: React.ReactNode;
}

/**
 * Empty state component
 */
export const EmptyState: React.FC<EmptyStateProps> = ({
  description = 'No data available',
  action,
  image,
}) => {
  return (
    <AntEmpty
      image={image || <InboxOutlined style={{ fontSize: 64, color: '#d9d9d9' }} />}
      description={description}
    >
      {action && (
        <Button type="primary" onClick={action.onClick}>
          {action.text}
        </Button>
      )}
    </AntEmpty>
  );
};
